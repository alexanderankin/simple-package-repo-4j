package simple.repo.winget;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import simple.repo.io.RepoIo;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;
import simple.repo.repository.Repository;
import simple.repo.repository.RepositoryBuilder;
import simple.repo.repository.RepositoryInitialization;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;

@Data
@Accessors(chain = true)
public class WingetRepoBuilder implements RepositoryBuilder {
    public static final String CATALOG_PATH = ".simple-repo/winget-catalog.json";
    public static final String CERTIFICATE_PATH = "simple-repo-winget.cer";
    private WingetPackageBuilder packageBuilder;
    private JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private String baseUrl;
    private String sourceIdentity = "Simple.Repo.Source";
    private String sourcePublisher = "CN=Simple Repo";

    @Override
    public List<String> defaultTargets() {
        return List.of(RepoOutputWinget.TARGET);
    }

    @Override
    public String targetFromIndexPath(Repository.RepositoryPath indexPath) {
        return RepoOutputWinget.TARGET;
    }

    @Override
    public PackageConfig prepareTarget(PackageConfig packageConfig, String target) {
        requireTarget(target);
        if (packageConfig.getControl() == null) packageConfig.setControl(new PackageConfig.ControlExtras());
        if (packageConfig.getFiles() == null) packageConfig.setFiles(new PackageConfig.FileSpec());
        var meta = packageConfig.getMeta();
        if (packageConfig.getControl().getMaintainer() == null || packageConfig.getControl().getMaintainer().isBlank())
            packageConfig.getControl().setMaintainer(text(meta.getReleaser(), "Simple Repo"));
        if (packageConfig.getControl().getDescription() == null || packageConfig.getControl().getDescription().isBlank())
            packageConfig.getControl().setDescription(meta.getName());
        return packageConfig;
    }

    @Override
    @SneakyThrows
    public void prepareSigning(byte[] privateKey, byte[] publicKey) {
        var certificate = certificate(publicKey);
        sourcePublisher = certificate.getSubjectX500Principal().getName();
        var keyStore = keyStore(privateKey);
        var alias = signingAlias(keyStore);
        if (!Arrays.equals(certificate.getEncoded(), keyStore.getCertificate(alias).getEncoded()))
            throw new IllegalArgumentException("WinGet CER does not match the signing certificate in the PFX");
    }

    @Override
    public Repository.RepositoryPath packagePath(String target, PackageConfig config) {
        requireTarget(target);
        return Repository.RepositoryPath.of(List.of("packages", packageBuilder.fileName(config)));
    }

    @Override
    @SneakyThrows
    public Map<String, FileIntegrityWithContent> build(Map<String, List<IndexFile>> packagesByTarget, Instant now) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalStateException("WinGet static repository requires baseUrl (set " + RepoOutputWinget.BASE_URL_PROPERTY + ")");
        var packages = new ArrayList<IndexFile>();
        packagesByTarget.forEach((target, values) -> {
            requireTarget(target);
            packages.addAll(values);
        });
        packages.sort(Comparator.comparing(index -> index.getPackageConfig().getMeta()));

        var files = new LinkedHashMap<String, FileIntegrityWithContent>();
        var manifests = new ArrayList<ManifestRecord>();
        var manifestGroups = packages.stream().collect(java.util.stream.Collectors.groupingBy(
                index -> identifier(index.getPackageConfig().getMeta().getName()) + "\u0000" +
                        index.getPackageConfig().getMeta().getVersion(), LinkedHashMap::new,
                java.util.stream.Collectors.toList()));
        for (var group : manifestGroups.values()) {
            var config = group.getFirst().getPackageConfig();
            var manifest = renderManifest(group);
            var hash = java.security.MessageDigest.getInstance("SHA-256").digest(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var path = manifestPath(config, hash);
            files.put(path, FileIntegrityWithContent.of(manifest, path));
            manifests.add(new ManifestRecord(config, path, hash, command(config)));
        }
        var database = database(manifests, now);
        files.put("source.msix", FileIntegrityWithContent.of(
                new WingetMsixBuilder().build(database, now, sourceIdentity, sourcePublisher), "source.msix"));
        files.put(CATALOG_PATH, FileIntegrityWithContent.of(jsonMapper.writeValueAsBytes(packages), CATALOG_PATH));
        return files;
    }

    @Override
    @SneakyThrows
    public List<IndexFile> readPublished(RepoIo<?> repoIo, String target, Collection<IndexFile> additions,
                                         RepositoryInitialization initialization) {
        requireTarget(target);
        if (initialization == RepositoryInitialization.re_init) throw new UnsupportedOperationException("re-init");
        if (initialization == RepositoryInitialization.init) return List.of();
        try {
            return Arrays.asList(jsonMapper.readValue(
                    repoIo.downloadPackage(Repository.RepositoryPath.of(List.of(".simple-repo", "winget-catalog.json"))),
                    IndexFile[].class));
        } catch (RepoIo.ObjectNotFoundException exception) {
            if (initialization == RepositoryInitialization.allowed) return List.of();
            throw new IllegalStateException("WinGet repository is not initialized", exception);
        }
    }

    @Override
    @SneakyThrows
    public Map<String, FileIntegrityWithContent> sign(Map<String, FileIntegrityWithContent> repositoryFiles,
                                                      byte[] privateKey, byte[] publicKey, Instant now) {
        var source = repositoryFiles.get("source.msix");
        if (source == null) throw new IllegalArgumentException("repository files do not contain source.msix");
        var keyStore = keyStore(privateKey);
        var alias = signingAlias(keyStore);
        var path = Files.createTempFile("simple-repo-winget-source-", ".msix");
        try {
            Files.write(path, source.getContent());
            // WinGet validates source packages with whole-chain revocation checking and cache-only URL retrieval.
            // A public timestamp chain can therefore make an otherwise valid self-signed source fail on a fresh client.
            var signer = new net.jsign.AuthenticodeSigner(keyStore, alias, "").withTimestamping(false);
            try (var signable = net.jsign.Signable.of(path.toFile())) {
                signer.sign(signable);
            }
            return Map.of(
                    "source.msix", FileIntegrityWithContent.of(Files.readAllBytes(path), "source.msix"),
                    CERTIFICATE_PATH, FileIntegrityWithContent.of(publicKey, CERTIFICATE_PATH));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private String renderManifest(List<IndexFile> indexes) {
        var config = indexes.getFirst().getPackageConfig();
        var meta = config.getMeta();
        var publisher = text(config.getControl().getMaintainer(), text(meta.getReleaser(), "Simple Repo"));
        var description = text(config.getControl().getDescription(), meta.getName());
        var result = new StringBuilder("""
                PackageIdentifier: %s
                PackageVersion: %s
                PackageLocale: en-US
                Publisher: %s
                PackageName: %s
                License: Proprietary
                ShortDescription: %s
                Installers:
                """.formatted(identifier(meta.getName()), yaml(meta.getVersion()), yaml(publisher), yaml(meta.getName()),
                yaml(description)));
        for (var index : indexes) {
            var installerConfig = index.getPackageConfig();
            var executable = packageBuilder.executable(installerConfig);
            var alias = commandName(executable);
            var installerUrl = URI.create(normalizedBaseUrl()).resolve("packages/" + packageBuilder.fileName(installerConfig));
            result.append("""
                - Architecture: %s
                  InstallerType: zip
                  NestedInstallerType: portable
                  InstallerUrl: %s
                  InstallerSha256: %s
                  NestedInstallerFiles:
                  - RelativeFilePath: %s
                    PortableCommandAlias: %s
                  Commands:
                  - %s
                """.formatted(packageBuilder.archName(installerConfig.getMeta().getArch()), yaml(installerUrl.toString()),
                    index.getFileIntegrity().getSha256().toUpperCase(Locale.ROOT), yaml(executable), yaml(alias), yaml(alias)));
        }
        return result.append("""
                ManifestType: merged
                ManifestVersion: 1.6.0
                """).toString();
    }

    private String manifestPath(PackageConfig config, byte[] hash) {
        var id = identifier(config.getMeta().getName());
        var first = id.substring(0, 1).toLowerCase(Locale.ROOT);
        var leaf = java.util.HexFormat.of().formatHex(hash, 0, 6);
        return "manifests/" + first + "/" + id + "/" + safeSegment(config.getMeta().getVersion()) + "/" + leaf;
    }

    @SneakyThrows
    private byte[] database(List<ManifestRecord> records, Instant now) {
        var path = Files.createTempFile("simple-repo-winget-", ".db");
        try {
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
                createSchema(connection, now);
                for (var record : records) insertManifest(connection, record);
            }
            return Files.readAllBytes(path);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private void createSchema(Connection c, Instant now) throws Exception {
        try (var s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE metadata(name TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL) WITHOUT ROWID");
            for (var table : List.of("ids:id", "names:name", "monikers:moniker", "versions:version", "channels:channel", "tags:tag", "commands:command")) {
                var parts = table.split(":");
                s.executeUpdate("CREATE TABLE " + parts[0] + "(rowid INTEGER PRIMARY KEY," + parts[1] + " TEXT NOT NULL)");
                s.executeUpdate("CREATE UNIQUE INDEX " + parts[0] + "_value_index ON " + parts[0] + "(" + parts[1] + ")");
            }
            s.executeUpdate("CREATE TABLE pathparts(rowid INTEGER PRIMARY KEY,parent INT64,pathpart TEXT NOT NULL)");
            s.executeUpdate("CREATE UNIQUE INDEX pathparts_unique ON pathparts(parent,pathpart)");
            s.executeUpdate("CREATE TABLE manifest(rowid INTEGER PRIMARY KEY,id INT64 NOT NULL,name INT64 NOT NULL,moniker INT64 NOT NULL,version INT64 NOT NULL,channel INT64 NOT NULL,pathpart INT64 NOT NULL,hash BLOB)");
            s.executeUpdate("CREATE INDEX manifest_id_index ON manifest(id)");
            s.executeUpdate("CREATE INDEX manifest_name_index ON manifest(name)");
            s.executeUpdate("CREATE INDEX manifest_moniker_index ON manifest(moniker)");
            s.executeUpdate("CREATE TABLE tags_map(manifest INT64 NOT NULL,tag INT64 NOT NULL,PRIMARY KEY(tag,manifest)) WITHOUT ROWID");
            s.executeUpdate("CREATE TABLE commands_map(manifest INT64 NOT NULL,command INT64 NOT NULL,PRIMARY KEY(command,manifest)) WITHOUT ROWID");
        }
        metadata(c, "majorVersion", "1");
        metadata(c, "minorVersion", "0");
        metadata(c, "databaseIdentifier", "{" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "}");
        metadata(c, "lastwritetime", Long.toString(now.getEpochSecond()));
    }

    private void insertManifest(Connection c, ManifestRecord record) throws Exception {
        var id = identifier(record.config.getMeta().getName());
        var idRow = value(c, "ids", "id", id);
        var nameRow = value(c, "names", "name", record.config.getMeta().getName());
        var monikerRow = value(c, "monikers", "moniker", record.command);
        var versionRow = value(c, "versions", "version", record.config.getMeta().getVersion());
        var channelRow = value(c, "channels", "channel", "");
        Long parent = null;
        for (var part : record.path.split("/")) parent = pathPart(c, parent, part);
        try (var statement = c.prepareStatement("INSERT INTO manifest(id,name,moniker,version,channel,pathpart,hash) VALUES(?,?,?,?,?,?,?)")) {
            statement.setLong(1, idRow); statement.setLong(2, nameRow); statement.setLong(3, monikerRow);
            statement.setLong(4, versionRow); statement.setLong(5, channelRow); statement.setLong(6, parent);
            statement.setBytes(7, record.hash); statement.executeUpdate();
        }
        var manifestId = lastId(c);
        var command = value(c, "commands", "command", record.command);
        try (var statement = c.prepareStatement("INSERT INTO commands_map(manifest,command) VALUES(?,?)")) {
            statement.setLong(1, manifestId); statement.setLong(2, command); statement.executeUpdate();
        }
    }

    private long value(Connection c, String table, String column, String value) throws Exception {
        try (var query = c.prepareStatement("SELECT rowid FROM " + table + " WHERE " + column + "=?")) {
            query.setString(1, value); var result = query.executeQuery(); if (result.next()) return result.getLong(1);
        }
        try (var insert = c.prepareStatement("INSERT INTO " + table + "(" + column + ") VALUES(?)")) {
            insert.setString(1, value); insert.executeUpdate();
        }
        return lastId(c);
    }

    private long pathPart(Connection c, Long parent, String value) throws Exception {
        var sql = parent == null ? "SELECT rowid FROM pathparts WHERE parent IS NULL AND pathpart=?" :
                "SELECT rowid FROM pathparts WHERE parent=? AND pathpart=?";
        try (var query = c.prepareStatement(sql)) {
            var offset = 1; if (parent != null) query.setLong(offset++, parent); query.setString(offset, value);
            var result = query.executeQuery(); if (result.next()) return result.getLong(1);
        }
        try (var insert = c.prepareStatement("INSERT INTO pathparts(parent,pathpart) VALUES(?,?)")) {
            if (parent == null) insert.setNull(1, java.sql.Types.BIGINT); else insert.setLong(1, parent);
            insert.setString(2, value); insert.executeUpdate();
        }
        return lastId(c);
    }

    private long lastId(Connection c) throws Exception {
        try (var statement = c.createStatement(); var result = statement.executeQuery("SELECT last_insert_rowid()")) {
            return result.getLong(1);
        }
    }

    private void metadata(Connection c, String name, String value) throws Exception {
        try (var statement = c.prepareStatement("INSERT INTO metadata(name,value) VALUES(?,?)")) {
            statement.setString(1, name); statement.setString(2, value); statement.executeUpdate();
        }
    }

    private String identifier(String name) {
        var result = name.replaceAll("[^A-Za-z0-9.-]", "-");
        return result.contains(".") ? result : "SimpleRepo." + result;
    }

    private String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._+-]", "-");
    }

    private String command(PackageConfig config) {
        return commandName(packageBuilder.executable(config));
    }

    private String commandName(String path) {
        var name = path.substring(path.lastIndexOf('/') + 1);
        var extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }

    @SneakyThrows
    private X509Certificate certificate(byte[] encoded) {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    @SneakyThrows
    private KeyStore keyStore(byte[] encoded) {
        var result = KeyStore.getInstance("PKCS12");
        result.load(new ByteArrayInputStream(encoded), new char[0]);
        return result;
    }

    @SneakyThrows
    private String signingAlias(KeyStore keyStore) {
        var aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            var alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) return alias;
        }
        throw new IllegalArgumentException("WinGet PFX contains no private key");
    }

    private String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String yaml(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void requireTarget(String target) {
        if (!RepoOutputWinget.TARGET.equals(target))
            throw new IllegalArgumentException("WinGet has one target named '" + RepoOutputWinget.TARGET + "'");
    }

    private record ManifestRecord(PackageConfig config, String path, byte[] hash, String command) {}
}
