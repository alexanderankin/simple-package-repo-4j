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
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.PackageBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Data
@Accessors(chain = true)
@Slf4j
public class DebPackageBuilder implements PackageBuilder {
    Path current = Path.of(System.getProperty("user.dir"));
    RestClient restClient = RestClient.create();

    @Override
    public String outputType() {
        return RepoOutputDeb.OUTPUT_NAME;
    }

    @Override
    public String archName(Arch arch) {
        return Objects.requireNonNull(DebArch.fromJdkArchName(arch.name()), () -> "unknown arch: " + arch).name();
    }

    @Override
    public String fileName(PackageConfig packageConfig) {
        PackageConfig.PackageMeta meta = packageConfig.getMeta();
        return meta.getName() + "_" + meta.getVersion() + "_" + archName(meta.getArch()) + ".deb";
    }

    @Override
    public FileIntegrityWithContent buildPackage(PackageConfig config) {
        byte[] arArchive = buildDebToArchive(config);
        String debFilename = fileName(config);
        return FileIntegrityWithContent.of(arArchive, debFilename);
    }

    @SneakyThrows
    public byte[] buildDebToArchive(PackageConfig config) {
        byte[] dataTarGz = createTarGz(
                Optional.ofNullable(config.getFiles().getDataFiles()).orElseGet(List::of),
                List.of()
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
                List.of(new PackageConfig.TarFileSpec.TextTarFileSpec()
                        .setContent(renderControl(config))
                        .setPath("control")
                        .setMode(null))
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
    private byte[] createTarGz(List<PackageConfig.TarFileSpec> files, List<PackageConfig.TarFileSpec> extra) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            List<PackageConfig.TarFileSpec> allFiles = new ArrayList<>(files);
            allFiles.addAll(extra);

            for (PackageConfig.TarFileSpec f : allFiles) {

                byte[] content = switch (f) {
                    case PackageConfig.TarFileSpec.TextTarFileSpec text -> text.getContent().getBytes();
                    case PackageConfig.TarFileSpec.BinaryTarFileSpec bin -> bin.getContent();
                    case PackageConfig.TarFileSpec.FileTarFileSpec fs ->
                            Files.readAllBytes(current.resolve(fs.getSourcePath()));
                    case PackageConfig.TarFileSpec.UrlTarFileSpec fs -> downloadUrlTarFile(fs);
                };
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


    private byte[] downloadUrlTarFile(PackageConfig.TarFileSpec.UrlTarFileSpec fs) {
        byte[] result = restClient.get()
                .uri(fs.getUrl())
                .headers(h -> {
                    if (!CollectionUtils.isEmpty(fs.getHeaders()))
                        h.putAll(fs.getHeaders());
                    if (fs.getBearerToken() != null)
                        h.setBearerAuth(fs.getBearerToken());
                })
                .retrieve()
                .body(byte[].class);
        Assert.isTrue(result != null, "have result");
        return result;
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
