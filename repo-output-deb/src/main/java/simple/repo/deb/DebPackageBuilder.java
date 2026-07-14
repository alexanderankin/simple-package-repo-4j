package simple.repo.deb;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.util.StringUtils;
import simple.repo.model.*;
import simple.repo.packaging.FileSpecReader;
import simple.repo.packaging.PackageBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Data
@Accessors(chain = true)
@Slf4j
public class DebPackageBuilder implements PackageBuilder {
    FileSpecReader fileSpecReader = new FileSpecReader();

    @Override
    public String outputType() {
        return RepoOutputDeb.OUTPUT_NAME;
    }

    @Override
    public String archName(Arch arch) {
        if (arch == null)
            return "all";
        return Objects.requireNonNull(DebArch.fromArch(arch), () -> "unknown arch: " + arch).name();
    }

    @Override
    public String fileName(PackageConfig packageConfig) {
        PackageConfig.PackageMeta meta = packageConfig.getMeta();
        var stringBuilder = new StringBuilder(meta.getName());
        stringBuilder.append('_').append(meta.getVersion());

        if (StringUtils.hasText(meta.getReleaseVersion()))
            stringBuilder.append('-').append(meta.getReleaseVersion());

        stringBuilder.append('_').append(archName(meta.getArch())).append(".deb");
        return stringBuilder.toString();
    }

    @Override
    public FileIntegrityWithContent buildPackage(PackageConfig config) {
        byte[] arArchive = buildDebToArchive(config);
        String debFilename = fileName(config);
        return FileIntegrityWithContent.of(arArchive, debFilename);
    }

    @Override
    public PackageConfig parseConfigFromPackage(byte[] downloadedPackage) {
        return DebParsePackageConfig.INSTANCE.parseConfigFromPackage(downloadedPackage);
    }

    @SneakyThrows
    public byte[] buildDebToArchive(PackageConfig config) {
        byte[] dataTarGz = createTarGz(
                Optional.ofNullable(config.getFiles().getDataFiles()).orElseGet(List::of),
                List.of(),
                config.getSettings()
        );

        int installedSize = 0;
        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(dataTarGz)))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isFile())
                    installedSize += (int) entry.getRealSize();
            }
        }
        config.getControl().setInstalledSize(installedSize / 1024);

        byte[] controlTarGz = createTarGz(
                Optional.ofNullable(config.getFiles().getControlFiles()).orElseGet(List::of),
                List.of(new PackageConfig.PkgFileSpec.TextPkgFileSpec()
                        .setContent(renderControl(config))
                        .setPath("control")
                        .setMode(null)),
                config.getSettings()
        );

        return createArArchive(List.of(
                Map.entry("debian-binary", "2.0\n".getBytes()),
                Map.entry("control.tar.gz", controlTarGz),
                Map.entry("data.tar.gz", dataTarGz)
        ));
    }

    private String renderControl(PackageConfig config) {
        var meta = config.getMeta();
        var control = config.getControl();

        return String.format("""
                        Package: %s
                        Version: %s
                        Depends: %s
                        Recommends: %s
                        Section: %s
                        Priority: %s
                        Homepage: %s
                        Conflicts: %s
                        Architecture: %s
                        Installed-Size: %d
                        Maintainer: %s
                        Description: %s
                        """,
                // optional fields
                meta.getName(), meta.getVersion(), control.getDepends(), control.getRecommends(), control.getSection(), control.getPriority(),
                control.getHomepage(), control.getConflicts(), archName(meta.getArch()), control.getInstalledSize(),
                // required fields
                control.getMaintainer(), control.getDescription()
        ).strip() + "\n";

    }

    @SneakyThrows
    private byte[] createTarGz(List<PackageConfig.PkgFileSpec> files,
                               List<PackageConfig.PkgFileSpec> extra,
                               PackageConfig.Settings settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            List<PackageConfig.PkgFileSpec> allFiles = new ArrayList<>(files);
            allFiles.addAll(extra);

            Set<String> seenDirs = new HashSet<>();

            for (PackageConfig.PkgFileSpec f : allFiles) {
                if (f instanceof PackageConfig.PkgFileSpec.DirPkgFileSpec directory) {
                    addDirectoryTree(seenDirs, tarOut, directory, settings);
                    continue;
                }
                byte[] content = fileSpecReader.readContent(f);
                createDirs(seenDirs, tarOut, f.getPath(), settings);
                TarArchiveEntry entry = new TarArchiveEntry(f.getPath());
                entry.setSize(content.length);

                if (f.getMode() != null) {
                    entry.setMode(f.getMode());
                }

                tarOut.putArchiveEntry(entry);
                tarOut.write(content);
                tarOut.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    @SneakyThrows
    private void addDirectoryTree(Set<String> seenDirs,
                                  TarArchiveOutputStream tarOut,
                                  PackageConfig.PkgFileSpec.DirPkgFileSpec spec,
                                  PackageConfig.Settings settings) {
        var sourceRoot = fileSpecReader.getCurrent().resolve(spec.getSourcePath()).normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("directory source does not exist: " + sourceRoot);
        }
        var targetRoot = spec.getPath().replaceFirst("^/+", "").replaceFirst("/+$", "");
        try (var paths = Files.walk(sourceRoot)) {
            for (var source : paths.sorted().toList()) {
                var relative = sourceRoot.relativize(source).toString().replace(source.getFileSystem().getSeparator(), "/");
                var target = relative.isEmpty() ? targetRoot : targetRoot + "/" + relative;
                if (Files.isDirectory(source)) {
                    var directoryPath = target + "/";
                    createDirs(seenDirs, tarOut, directoryPath, settings);
                    if (seenDirs.add(directoryPath)) {
                        var entry = new TarArchiveEntry(directoryPath);
                        entry.setMode(mode(spec, source, relative, true, settings));
                        tarOut.putArchiveEntry(entry);
                        tarOut.closeArchiveEntry();
                    }
                } else if (Files.isRegularFile(source)) {
                    createDirs(seenDirs, tarOut, target, settings);
                    var content = Files.readAllBytes(source);
                    var entry = new TarArchiveEntry(target);
                    entry.setSize(content.length);
                    entry.setMode(mode(spec, source, relative, false, settings));
                    tarOut.putArchiveEntry(entry);
                    tarOut.write(content);
                    tarOut.closeArchiveEntry();
                }
            }
        }
    }

    @SneakyThrows
    private int mode(PackageConfig.PkgFileSpec.DirPkgFileSpec spec,
                     Path source,
                     String relative,
                     boolean directory,
                     PackageConfig.Settings settings) {
        if (spec.getModeOverrides() != null && spec.getModeOverrides().containsKey(relative)) {
            return spec.getModeOverrides().get(relative);
        }
        if (spec.getModeMode() == PackageConfig.PkgFileSpec.DirPkgFileSpec.ModeMode.OVERRIDE && spec.getMode() != null) {
            return spec.getMode();
        }
        try {
            return posixMode(Files.getPosixFilePermissions(source));
        } catch (UnsupportedOperationException ignored) {
            if (directory) {
                return settings != null && settings.getDefaultDirMode() != null
                        ? settings.getDefaultDirMode()
                        : 0755;
            }
            return spec.getMode() == null ? 0644 : spec.getMode();
        }
    }

    private int posixMode(Set<PosixFilePermission> permissions) {
        var mode = 0;
        if (permissions.contains(PosixFilePermission.OWNER_READ)) mode |= 0400;
        if (permissions.contains(PosixFilePermission.OWNER_WRITE)) mode |= 0200;
        if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) mode |= 0100;
        if (permissions.contains(PosixFilePermission.GROUP_READ)) mode |= 0040;
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)) mode |= 0020;
        if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) mode |= 0010;
        if (permissions.contains(PosixFilePermission.OTHERS_READ)) mode |= 0004;
        if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) mode |= 0002;
        if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= 0001;
        return mode;
    }

    @SneakyThrows
    private void createDirs(Set<String> seenDirs, TarArchiveOutputStream tarOut, String path, PackageConfig.Settings settings) {
        int directoryMode;
        if (settings != null && settings.getDefaultDirMode() != null) {
            directoryMode = settings.getDefaultDirMode();
        } else {
            directoryMode = PackageConfig.Settings.DEFAULT_DIRECTORY_MODE_DEFAULT;
        }

        String normalized = path.startsWith("/") ? path.substring(1) : path;

        int slashIndex = normalized.indexOf('/');

        while (slashIndex >= 0) {
            String dirPath = normalized.substring(0, slashIndex + 1);

            if (seenDirs.add(dirPath)) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(dirPath);
                dirEntry.setMode(directoryMode);

                tarOut.putArchiveEntry(dirEntry);
                tarOut.closeArchiveEntry();
            }

            slashIndex = normalized.indexOf('/', slashIndex + 1);
        }
    }

    /**
     * order matters to debian packaging
     */
    @SneakyThrows
    private byte[] createArArchive(List<Map.Entry<String, byte[]>> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArArchiveOutputStream arOut = new ArArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries) {
                String name = entry.getKey();
                byte[] content = entry.getValue();
                ArArchiveEntry arEntry = new ArArchiveEntry(name, content.length);
                arOut.putArchiveEntry(arEntry);
                arOut.write(content);
                arOut.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }
}
