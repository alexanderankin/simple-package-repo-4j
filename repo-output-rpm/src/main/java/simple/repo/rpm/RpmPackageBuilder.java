package simple.repo.rpm;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.springframework.util.StringUtils;
import simple.repo.model.Arch;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.FileSpecReader;
import simple.repo.packaging.PackageBuilder;
import simple.repo.rpm.model.Lead;
import simple.repo.rpm.model.Signature;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
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
        // needed up here for hashing and stuff
        var payload = buildPayload(config);

        var out = new ByteArrayOutputStream();

        out.write(new Lead()
                .setType(Lead.PackageType.binary)
                .setArchNumValue(switch (config.getMeta().getArch()) {
                    case amd64 -> Lead.ArchNum.x86_64;
                    case arm64 -> Lead.ArchNum.aarch64;
                    case riscv64 -> Lead.ArchNum.riscv64;
                    case null, default -> throw new UnsupportedOperationException();
                })
                .setNameString(fileName(config))
                .setOsNum(Lead.OsNum.linux)
                .setSignatureType(Lead.SignatureType.header)
                .toByteArray());

        {
            var sigEntries = new ArrayList<Signature.SignatureEntry>();

            out.write(new Signature.Index()
                    .setIntro(new Signature.Intro())
                    .setEntries(new ArrayList<>())
                    .addEntries(sigEntries)
                    .toByteArray());

            out.write(new Signature().setEntries(sigEntries).toByteArray());
        }

        {
            var headerEntries = new ArrayList<Signature.SignatureEntry>();

            out.write(new Signature.Index()
                    .setIntro(new Signature.Intro())
                    .setEntries(new ArrayList<>())
                    .addEntries(headerEntries)
                    .toByteArray());

            out.write(new Signature().setEntries(headerEntries).toByteArray());
        }

        var offset = 0; // todo calculate what it should be
        while (offset-- > 0)
            out.write(0);

        out.write(payload);

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
