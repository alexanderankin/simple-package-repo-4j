package simple.repo.rpm;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import simple.repo.keys.KeysUtils;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.PackageConfig;
import simple.repo.packaging.FileSpecReader;
import simple.repo.rpm.repomd.dto.FileListsDto;
import simple.repo.rpm.repomd.dto.OtherDto;
import simple.repo.rpm.repomd.dto.PrimaryDto;
import simple.repo.rpm.repomd.dto.RepoMdDto;
import simple.repo.rpm.repomd.dtoconfig.RpmXmlCustomizer;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class RpmRepoBuilder {
    private final XmlMapper xmlMapper = new RpmXmlCustomizer().customized(XmlMapper.builder()).build();
    private final FileSpecReader fileSpecReader = new FileSpecReader();

    public RepoBuilder repoBuilder(RepoConfig config) {
        return repoBuilder(config, Instant.now());
    }

    public RepoBuilder repoBuilder(RepoConfig config, Instant now) {
        return new RepoBuilder(config, now);
    }

    public PackageMeta packageMeta(PackageConfig config, FileIntegrityWithContent packageFile) {
        return new PackageMeta().setConfig(config).setPackageFile(packageFile);
    }

    public Map<String, FileIntegrityWithContent> buildRepo(Repo repo) {
        return repo.versionSections().values().stream()
                .flatMap(section -> section.repoFiles().entrySet().stream()
                        .map(file ->
                                Map.entry(section.getVersion() + "/" + file.getKey(), file.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (_, right) -> right, LinkedHashMap::new));
    }

    public Map<String, FileIntegrityWithContent> signRepo(Map<String, FileIntegrityWithContent> repoFiles,
                                                          byte[] privateKey,
                                                          byte[] publicKey,
                                                          Instant now) {
        var signed = new LinkedHashMap<String, FileIntegrityWithContent>();
        for (var file : repoFiles.entrySet()) {
            if (!file.getKey().endsWith("/repodata/repomd.xml")) {
                continue;
            }
            var signaturePath = file.getKey() + ".asc";
            signed.put(signaturePath, FileIntegrityWithContent.of(
                    KeysUtils.generateDetachedSig(privateKey, file.getValue().getContent(), now, null),
                    signaturePath));
            var root = file.getKey().substring(0, file.getKey().length() - "repodata/repomd.xml".length());
            var publicKeyPath = root + "repository.asc";
            signed.put(publicKeyPath, FileIntegrityWithContent.of(publicKey, publicKeyPath));
        }
        return signed;
    }

    private Map<String, FileIntegrityWithContent> metadata(List<PackageMeta> packages,
                                                           Instant now,
                                                           int databaseVersion) {
        var primary = xmlMapper.writeValueAsBytes(primary(packages, now));
        var filelists = xmlMapper.writeValueAsBytes(filelists(packages));
        var other = xmlMapper.writeValueAsBytes(other(packages));

        var artifacts = new ArrayList<Artifact>();
        artifacts.add(compressedXml(RepoMdDto.DataType.primary, "primary.xml.gz", primary));
        artifacts.add(compressedXml(RepoMdDto.DataType.filelists, "filelists.xml.gz", filelists));
        artifacts.add(compressedXml(RepoMdDto.DataType.other, "other.xml.gz", other));
        artifacts.add(compressedDatabase(RepoMdDto.DataType.primary_db, "primary.sqlite.bz2",
                primaryDatabase(packages, primary, databaseVersion), databaseVersion));
        artifacts.add(compressedDatabase(RepoMdDto.DataType.filelists_db, "filelists.sqlite.bz2",
                filelistsDatabase(packages, filelists, databaseVersion), databaseVersion));
        artifacts.add(compressedDatabase(RepoMdDto.DataType.other_db, "other.sqlite.bz2",
                otherDatabase(packages, other, databaseVersion), databaseVersion));

        var repoMd = new RepoMdDto().setRevision(now).setData(artifacts.stream()
                .map(artifact -> artifact.repoData(now))
                .toList());
        var result = new LinkedHashMap<String, FileIntegrityWithContent>();
        for (var artifact : artifacts) {
            result.put(artifact.path(), FileIntegrityWithContent.of(artifact.compressed(), artifact.path()));
        }
        result.put("repodata/repomd.xml", FileIntegrityWithContent.of(xmlMapper.writeValueAsBytes(repoMd), "repodata/repomd.xml"));
        return result;
    }

    private PrimaryDto primary(List<PackageMeta> packages, Instant now) {
        return new PrimaryDto()
                .setPackages(packages.size())
                .setPackageList(packages.stream().map(meta -> primaryPackage(meta, now)).toList());
    }

    private PrimaryDto.Package primaryPackage(PackageMeta packageMeta, Instant now) {
        var config = packageMeta.getConfig();
        var meta = config.getMeta();
        var control = config.getControl();
        var integrity = packageMeta.getPackageFile().getFileIntegrity();
        var release = release(meta);
        var arch = new RpmPackageBuilder().archName(meta.getArch());
        var files = filePaths(config);
        var provides = new PrimaryDto.Entries().setEntryList(List.of(
                new PrimaryDto.Entry().setName(meta.getName()).setFlags("EQ")
                        .setEpoch("0").setVer(meta.getVersion()).setRel(release)));
        return new PrimaryDto.Package()
                .setType("rpm")
                .setName(meta.getName())
                .setArch(arch)
                .setVersion(new PrimaryDto.Version().setEpoch("0").setVer(meta.getVersion()).setRel(release))
                .setChecksum(new PrimaryDto.Checksum().setType("sha256").setPkgId("YES").setValue(integrity.getSha256()))
                .setSummary(summary(config))
                .setDescription(description(config))
                .setPackager(packager(config))
                .setUrl(control == null ? "" : control.getHomepage())
                .setTime(new PrimaryDto.Time().setFile(now.getEpochSecond()).setBuild(now.getEpochSecond()))
                .setSize(new PrimaryDto.Size().setPackageSize((long) integrity.getSize())
                        .setInstalled(installedSize(config)).setArchive(installedSize(config)))
                .setLocation(new PrimaryDto.Location().setHref("pool/" + integrity.getPath()))
                .setFormat(new PrimaryDto.Format()
                        .setLicense("unknown")
                        .setGroup(control == null ? "Applications/System" : control.getSection())
                        .setBuildhost("localhost")
                        .setProvides(provides)
                        .setFiles(files));
    }

    private FileListsDto filelists(List<PackageMeta> packages) {
        return new FileListsDto().setPackageCount(packages.size()).setPackageData(packages.stream()
                .map(meta -> new FileListsDto.Package()
                        .setPkgId(meta.getPackageFile().getFileIntegrity().getSha256())
                        .setName(meta.getConfig().getMeta().getName())
                        .setArch(new RpmPackageBuilder().archName(meta.getConfig().getMeta().getArch()))
                        .setVersion(version(meta.getConfig().getMeta()))
                        .setFileItems(filePaths(meta.getConfig()).stream()
                                .map(path -> new FileListsDto.Package.FileItem().setText(path)).toList()))
                .toList());
    }

    private OtherDto other(List<PackageMeta> packages) {
        return new OtherDto().setPackageCount(packages.size()).setPackages(packages.stream()
                .map(meta -> new OtherDto.PackageEntry()
                        .setPkgId(meta.getPackageFile().getFileIntegrity().getSha256())
                        .setName(meta.getConfig().getMeta().getName())
                        .setArch(new RpmPackageBuilder().archName(meta.getConfig().getMeta().getArch()))
                        .setVersion(version(meta.getConfig().getMeta()))
                        .setChangelogs(List.of()))
                .toList());
    }

    private OtherDto.Version version(PackageConfig.PackageMeta meta) {
        return new OtherDto.Version().setEpoch("0").setVersion(meta.getVersion()).setRelease(release(meta));
    }

    private List<String> filePaths(PackageConfig config) {
        return Objects.requireNonNullElse(config.getFiles().getDataFiles(), List.<PackageConfig.PkgFileSpec>of())
                .stream().map(PackageConfig.PkgFileSpec::getPath)
                .map(path -> path.startsWith("/") ? path : "/" + path)
                .toList();
    }

    private long installedSize(PackageConfig config) {
        if (config.getControl() != null && config.getControl().getInstalledSize() != null) {
            return config.getControl().getInstalledSize() * 1024L;
        }
        return Objects.requireNonNullElse(config.getFiles().getDataFiles(), List.<PackageConfig.PkgFileSpec>of())
                .stream().mapToLong(spec -> fileSpecReader.readContent(spec).length).sum();
    }

    private String summary(PackageConfig config) {
        return description(config).lines().findFirst().orElse(config.getMeta().getName());
    }

    private String description(PackageConfig config) {
        return config.getControl() == null || config.getControl().getDescription().isBlank()
                ? config.getMeta().getName() : config.getControl().getDescription();
    }

    private String packager(PackageConfig config) {
        if (config.getMeta().getReleaser() != null && !config.getMeta().getReleaser().isBlank()) {
            return config.getMeta().getReleaser();
        }
        return config.getControl() == null ? "" : config.getControl().getMaintainer();
    }

    private String release(PackageConfig.PackageMeta meta) {
        var release = meta.getReleaseVersion() == null || meta.getReleaseVersion().isBlank()
                ? "1" : meta.getReleaseVersion();
        return meta.getElVersion() == null || meta.getElVersion().isBlank()
                ? release : release + "." + meta.getElVersion();
    }

    private Artifact compressedXml(RepoMdDto.DataType type, String name, byte[] open) {
        return artifact(type, name, open, gzip(open), null);
    }

    private Artifact compressedDatabase(RepoMdDto.DataType type, String name, byte[] open, int databaseVersion) {
        return artifact(type, name, open, bzip2(open), databaseVersion);
    }

    private Artifact artifact(RepoMdDto.DataType type, String name, byte[] open, byte[] compressed,
                              Integer databaseVersion) {
        var checksum = DigestUtils.sha256Hex(compressed);
        return new Artifact(type, "repodata/" + checksum + "-" + name, open, compressed, databaseVersion);
    }

    @SneakyThrows
    private byte[] gzip(byte[] content) {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(content);
        }
        return out.toByteArray();
    }

    @SneakyThrows
    private byte[] bzip2(byte[] content) {
        var out = new ByteArrayOutputStream();
        try (var bzip = new BZip2CompressorOutputStream(out)) {
            bzip.write(content);
        }
        return out.toByteArray();
    }

    private byte[] primaryDatabase(List<PackageMeta> packages, byte[] source, int version) {
        return sqlite(source, version, statement -> {
            statement.accept("CREATE TABLE packages (pkgKey INTEGER PRIMARY KEY, pkgId TEXT, name TEXT, arch TEXT, version TEXT, epoch TEXT, release TEXT, summary TEXT, description TEXT, url TEXT, time_file INTEGER, time_build INTEGER, rpm_license TEXT, rpm_vendor TEXT, rpm_group TEXT, rpm_buildhost TEXT, rpm_sourcerpm TEXT, rpm_header_start INTEGER, rpm_header_end INTEGER, rpm_packager TEXT, size_package INTEGER, size_installed INTEGER, size_archive INTEGER, location_href TEXT, location_base TEXT, checksum_type TEXT)");
            statement.accept("CREATE TABLE files (name TEXT, type TEXT, pkgKey INTEGER)");
            for (var table : List.of("requires", "provides", "conflicts", "obsoletes")) {
                statement.accept("CREATE TABLE " + table + " (name TEXT, flags TEXT, epoch TEXT, version TEXT, release TEXT, pkgKey INTEGER, pre BOOL DEFAULT FALSE)");
            }
            for (var i = 0; i < packages.size(); i++) {
                var packageMeta = packages.get(i);
                var config = packageMeta.getConfig();
                var meta = config.getMeta();
                var control = config.getControl();
                var integrity = packageMeta.getPackageFile().getFileIntegrity();
                var key = i + 1;
                statement.accept("INSERT INTO packages VALUES (" + key + "," + q(integrity.getSha256()) + "," + q(meta.getName()) + "," + q(new RpmPackageBuilder().archName(meta.getArch())) + "," + q(meta.getVersion()) + ",'0'," + q(release(meta)) + "," + q(summary(config)) + "," + q(description(config)) + "," + q(control == null ? "" : control.getHomepage()) + ",0,0,'unknown',''," + q(control == null ? "Applications/System" : control.getSection()) + ",'localhost','',0,0," + q(packager(config)) + "," + integrity.getSize() + "," + installedSize(config) + "," + installedSize(config) + "," + q("pool/" + integrity.getPath()) + ",'','sha256')");
                statement.accept("INSERT INTO provides VALUES (" + q(meta.getName()) + ",'EQ','0'," + q(meta.getVersion()) + "," + q(release(meta)) + "," + key + ",0)");
                for (var path : filePaths(config)) {
                    statement.accept("INSERT INTO files VALUES (" + q(path) + ",'file'," + key + ")");
                }
            }
        });
    }

    private byte[] filelistsDatabase(List<PackageMeta> packages, byte[] source, int version) {
        return sqlite(source, version, statement -> {
            statement.accept("CREATE TABLE packages (pkgKey INTEGER PRIMARY KEY, pkgId TEXT, name TEXT, arch TEXT, version TEXT, epoch TEXT, release TEXT)");
            statement.accept("CREATE TABLE filelist (pkgKey INTEGER, dirname TEXT, filenames TEXT, filetypes TEXT)");
            for (var i = 0; i < packages.size(); i++) {
                var packageMeta = packages.get(i);
                var meta = packageMeta.getConfig().getMeta();
                var key = i + 1;
                statement.accept("INSERT INTO packages VALUES (" + key + "," + q(packageMeta.getPackageFile().getFileIntegrity().getSha256()) + "," + q(meta.getName()) + "," + q(new RpmPackageBuilder().archName(meta.getArch())) + "," + q(meta.getVersion()) + ",'0'," + q(release(meta)) + ")");
                for (var path : filePaths(packageMeta.getConfig())) {
                    var slash = path.lastIndexOf('/');
                    statement.accept("INSERT INTO filelist VALUES (" + key + "," + q(path.substring(0, slash + 1)) + "," + q(path.substring(slash + 1)) + ",'f')");
                }
            }
        });
    }

    private byte[] otherDatabase(List<PackageMeta> packages, byte[] source, int version) {
        return sqlite(source, version, statement -> {
            statement.accept("CREATE TABLE packages (pkgKey INTEGER PRIMARY KEY, pkgId TEXT, name TEXT, arch TEXT, version TEXT, epoch TEXT, release TEXT)");
            statement.accept("CREATE TABLE changelog (pkgKey INTEGER, author TEXT, date INTEGER, changelog TEXT)");
            for (var i = 0; i < packages.size(); i++) {
                var packageMeta = packages.get(i);
                var meta = packageMeta.getConfig().getMeta();
                statement.accept("INSERT INTO packages VALUES (" + (i + 1) + "," + q(packageMeta.getPackageFile().getFileIntegrity().getSha256()) + "," + q(meta.getName()) + "," + q(new RpmPackageBuilder().archName(meta.getArch())) + "," + q(meta.getVersion()) + ",'0'," + q(release(meta)) + ")");
            }
        });
    }

    @SneakyThrows
    private byte[] sqlite(byte[] source, int version, Consumer<Consumer<String>> schemaAndData) {
        var path = Files.createTempFile("simple-repo-rpm-", ".sqlite");
        try {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("PRAGMA journal_mode=OFF");
                statement.executeUpdate("CREATE TABLE db_info (dbversion INTEGER, checksum TEXT)");
                statement.executeUpdate("INSERT INTO db_info VALUES (" + version + "," + q(DigestUtils.sha256Hex(source)) + ")");
                schemaAndData.accept(sql -> {
                    try {
                        statement.executeUpdate(sql);
                    } catch (java.sql.SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
            return Files.readAllBytes(path);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private String q(String value) {
        return "'" + Objects.requireNonNullElse(value, "").replace("'", "''") + "'";
    }

    private record Artifact(RepoMdDto.DataType type, String path, byte[] open, byte[] compressed,
                            Integer databaseVersion) {
        RepoMdDto.RepoData repoData(Instant now) {
            return new RepoMdDto.RepoData()
                    .setType(type)
                    .setChecksum(checksum(compressed))
                    .setOpenChecksum(checksum(open))
                    .setLocation(new RepoMdDto.Location().setHref(path))
                    .setTimestamp(now)
                    .setSize(compressed.length)
                    .setOpenSize(open.length)
                    .setDatabaseVersion(databaseVersion);
        }

        private RepoMdDto.Checksum checksum(byte[] content) {
            return new RepoMdDto.Checksum().setType(RepoMdDto.ChecksumType.sha256)
                    .setValue(DigestUtils.sha256Hex(content));
        }
    }

    @Data
    @Accessors(chain = true)
    public static class RepoConfig {
        int databaseVersion = 10;
    }

    @Data
    @Accessors(chain = true)
    public static class PackageMeta {
        PackageConfig config;
        FileIntegrityWithContent packageFile;
    }

    @Data
    @Accessors(chain = true)
    public static class Repo {
        Map<String, VersionSection> versionSections;

        Map<String, VersionSection> versionSections() {
            if (versionSections == null) versionSections = new LinkedHashMap<>();
            return versionSections;
        }

        @Data
        @Accessors(chain = true)
        public static class VersionSection {
            final String version;
            Map<String, FileIntegrityWithContent> repoFiles;

            Map<String, FileIntegrityWithContent> repoFiles() {
                if (repoFiles == null) repoFiles = new LinkedHashMap<>();
                return repoFiles;
            }
        }
    }

    public class RepoBuilder {
        final RepoConfig config;
        final Instant now;
        final Repo repo = new Repo();

        RepoBuilder(RepoConfig config, Instant now) {
            this.config = config;
            this.now = now;
        }

        public VersionSectionBuilder buildVersion(String version) {
            var section = repo.versionSections().computeIfAbsent(version, Repo.VersionSection::new);
            return new VersionSectionBuilder(this, section);
        }

        public Repo build() {
            return repo;
        }
    }

    @RequiredArgsConstructor
    public class VersionSectionBuilder {
        @NonNull
        final RepoBuilder repoBuilder;
        @NonNull
        final Repo.VersionSection section;
        final List<PackageMeta> packages = new ArrayList<>();

        public VersionSectionBuilder addPackage(PackageMeta packageMeta) {
            packages.add(packageMeta);
            return this;
        }

        public RepoBuilder build() {
            var byArch = packages.stream()
                    .collect(Collectors.groupingBy(
                            meta -> new RpmPackageBuilder().archName(meta.getConfig().getMeta().getArch()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            for (var archPackages : byArch.entrySet()) {
                metadata(archPackages.getValue(), repoBuilder.now, repoBuilder.config.getDatabaseVersion())
                        .forEach((path, content) -> section.repoFiles().put(archPackages.getKey() + "/" + path, content));
            }
            return repoBuilder;
        }
    }
}
