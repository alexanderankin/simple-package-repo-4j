package simple.repo.winget;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.FileSpecReader;
import simple.repo.packaging.PackageBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Data
@Accessors(chain = true)
public class WingetPackageBuilder implements PackageBuilder {
    public static final String CONFIG_ENTRY = ".simple-repo/package.json";
    private FileSpecReader fileSpecReader = new FileSpecReader();
    private JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Override
    public String outputType() {
        return RepoOutputWinget.OUTPUT_NAME;
    }

    @Override
    public String archName(Arch arch) {
        return WingetArch.from(arch).name();
    }

    @Override
    public String fileName(PackageConfig config) {
        var meta = config.getMeta();
        return safe(meta.getName()) + "_" + safe(meta.getVersion()) + "_" + archName(meta.getArch()) + ".zip";
    }

    @Override
    public PackageConfig.PackageMeta metaFromFileName(String input) {
        var name = Path.of(input).getFileName().toString();
        if (name.endsWith(INDEX_JSON_FILE_EXTENSION))
            name = name.substring(0, name.length() - INDEX_JSON_FILE_EXTENSION.length());
        if (!name.endsWith(".zip")) throw new IllegalArgumentException("not a WinGet ZIP filename: " + input);
        var base = name.substring(0, name.length() - 4);
        var archAt = base.lastIndexOf('_');
        var versionAt = base.lastIndexOf('_', archAt - 1);
        if (versionAt <= 0 || archAt <= versionAt) throw new IllegalArgumentException("expected name_version_arch.zip: " + input);
        WingetArch arch;
        try {
            arch = WingetArch.valueOf(base.substring(archAt + 1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unrecognized WinGet architecture in " + input, exception);
        }
        return new PackageConfig.PackageMeta()
                .setName(base.substring(0, versionAt))
                .setVersion(base.substring(versionAt + 1, archAt))
                .setArch(arch.arch());
    }

    @Override
    @SneakyThrows
    public FileIntegrityWithContent buildPackage(PackageConfig config) {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var spec : Objects.requireNonNullElse(config.getFiles().getDataFiles(), List.<PackageConfig.PkgFileSpec>of())) {
                if (spec instanceof PackageConfig.PkgFileSpec.DirPkgFileSpec)
                    throw new IllegalArgumentException("WinGet portable ZIPs currently require explicit files");
                var path = entryPath(spec.getPath());
                if (path.equals(CONFIG_ENTRY)) throw new IllegalArgumentException("reserved WinGet package path: " + path);
                zip.putNextEntry(new ZipEntry(path));
                zip.write(fileSpecReader.readContent(spec));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(CONFIG_ENTRY));
            zip.write(jsonMapper.writeValueAsBytes(config));
            zip.closeEntry();
        }
        return FileIntegrityWithContent.of(output.toByteArray(), fileName(config));
    }

    @Override
    @SneakyThrows
    public PackageConfig parseConfigFromPackage(byte[] downloadedPackage) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(downloadedPackage))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (CONFIG_ENTRY.equals(entry.getName())) return jsonMapper.readValue(zip.readAllBytes(), PackageConfig.class);
            }
        }
        throw new IllegalArgumentException("WinGet package does not contain " + CONFIG_ENTRY);
    }

    public String executable(PackageConfig config) {
        return Objects.requireNonNullElse(config.getFiles().getDataFiles(), List.<PackageConfig.PkgFileSpec>of()).stream()
                .map(PackageConfig.PkgFileSpec::getPath)
                .map(this::entryPath)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("WinGet portable package requires a data file"));
    }

    private String entryPath(String path) {
        var normalized = path.replace('\\', '/').replaceFirst("^/+", "");
        if (normalized.isBlank() || normalized.equals("..") || normalized.startsWith("../") || normalized.contains("/../"))
            throw new IllegalArgumentException("invalid ZIP entry path: " + path);
        return normalized;
    }

    private String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
