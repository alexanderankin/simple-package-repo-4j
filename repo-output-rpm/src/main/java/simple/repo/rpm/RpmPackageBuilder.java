package simple.repo.rpm;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.FileSpecReader;
import simple.repo.packaging.PackageBuilder;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * @see <a href=http://ftp.rpm.org/max-rpm/s1-rpm-file-format-rpm-file-format.html>rpm.org: Appendix A. Format of the RPM File</a>
 */
@Data
@Accessors(chain = true)
public class RpmPackageBuilder implements PackageBuilder {

    FileSpecReader fileSpecReader = new FileSpecReader();

    @Override
    public String outputType() {
        return RepoOutputRpm.OUTPUT_NAME;
    }

    @Override
    public String archName(Arch arch) {
        if (arch == null)
            return "noarch";
        return Objects.requireNonNull(RpmArch.fromArch(arch), () -> "Unknown arch: " + arch).name();
    }

    /**
     * java-21-openjdk-javadoc-21.0.9.0.10-1.el10.aarch64.rpm
     * name-version-releaseversion.elversion.arch.rpm
     */
    @Override
    public String fileName(PackageConfig packageConfig) {
        PackageConfig.PackageMeta meta = packageConfig.getMeta();
        var stringBuilder = new StringBuilder(meta.getName());
        stringBuilder.append('-').append(meta.getVersion());

        if (StringUtils.hasText(meta.getReleaseVersion()))
            stringBuilder.append('-').append(meta.getReleaseVersion());

        stringBuilder.append('.').append(meta.getElVersion());
        stringBuilder.append('.').append(archName(meta.getArch())).append(".rpm");
        return stringBuilder.toString();
    }

    @Override
    @SneakyThrows
    public FileIntegrityWithContent buildPackage(PackageConfig config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageConfig parseConfigFromPackage(byte[] downloadedPackage) {
        throw new UnsupportedOperationException();
    }

    @SneakyThrows
    private byte[] buildPayload(PackageConfig config) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();

        try (var gzip = new GZIPOutputStream(raw);
             var cpio = new CpioArchiveOutputStream(gzip)) {

            for (PackageConfig.PkgFileSpec file : config.getFiles().getDataFiles()) {
                byte[] content = fileSpecReader.readContent(file);
                cpio.putArchiveEntry(new CpioArchiveEntry(file.getPath(), content.length));
                cpio.write(content);
            }

            cpio.finish();
        }

        return raw.toByteArray();
    }
}
