package simple.repo.winget;

import org.junit.jupiter.api.Test;
import simple.repo.model.Arch;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class WingetRepositoryTest {
    private static final YAMLMapper YAML = YAMLMapper.builder().findAndAddModules().build();

    @Test
    void buildsAndParsesPortableZipFromYaml() throws Exception {
        var config = config("Example.Hello", "1.2.3", Arch.amd64);
        var builder = new WingetPackageBuilder();

        var result = builder.buildPackage(config);

        assertEquals("Example.Hello_1.2.3_x64.zip", result.getFileIntegrity().getPath());
        assertEquals(config, builder.parseConfigFromPackage(result.getContent()));
        assertEquals(List.of("bin/example-hello.exe", WingetPackageBuilder.CONFIG_ENTRY), zipEntries(result.getContent()));
    }

    @Test
    void buildsStaticSourceForMultipleVersionsAndArchitectures() throws Exception {
        var packageBuilder = new WingetPackageBuilder();
        var indexes = new java.util.ArrayList<IndexFile>();
        for (var version : List.of("1.0.0", "2.0.0")) {
            for (var arch : List.of(Arch.amd64, Arch.arm64)) {
                var config = config("Example.Matrix", version, arch);
                var zip = packageBuilder.buildPackage(config);
                indexes.add(new IndexFile().setPackageConfig(config).setFileIntegrity(zip.getFileIntegrity()));
            }
        }
        var builder = new WingetRepoBuilder().setPackageBuilder(packageBuilder)
                .setBaseUrl("https://packages.example.invalid/winget/");

        var result = builder.build(Map.of(RepoOutputWinget.TARGET, indexes), Instant.parse("2026-07-16T18:00:00Z"));

        assertTrue(result.containsKey("source.msix"));
        assertTrue(result.containsKey(WingetRepoBuilder.CATALOG_PATH));
        assertEquals(2, result.keySet().stream().filter(path -> path.startsWith("manifests/")).count());
        for (var manifest : result.entrySet().stream().filter(entry -> entry.getKey().startsWith("manifests/")).toList()) {
            var yaml = new String(manifest.getValue().getContent(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("InstallerType: zip"));
            assertTrue(yaml.contains("NestedInstallerType: portable"));
            assertTrue(yaml.contains("https://packages.example.invalid/winget/packages/"));
            assertTrue(yaml.contains("Architecture: x64"));
            assertTrue(yaml.contains("Architecture: arm64"));
        }

        var source = result.get("source.msix").getContent();
        assertTrue(zipEntries(source).containsAll(List.of(
                "Public/index.db", "AppxManifest.xml", "AppxBlockMap.xml", "[Content_Types].xml")));
        var database = zipEntry(source, "Public/index.db");
        var db = Files.createTempFile("winget-index-test", ".db");
        try {
            Files.write(db, database);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db);
                 var statement = connection.createStatement()) {
                assertEquals("1", query(statement, "SELECT value FROM metadata WHERE name='majorVersion'"));
                assertEquals("0", query(statement, "SELECT value FROM metadata WHERE name='minorVersion'"));
                assertEquals("2", query(statement, "SELECT count(*) FROM manifest"));
                assertEquals("Example.Matrix", query(statement, "SELECT id FROM ids WHERE id='Example.Matrix'"));
                assertEquals("example-hello", query(statement, "SELECT command FROM commands LIMIT 1"));
            }
        } finally {
            Files.deleteIfExists(db);
        }
    }

    @Test
    void serviceLoaderExposesWingetRepository() {
        var repository = java.util.ServiceLoader.load(simple.repo.repository.Repository.class).stream()
                .map(java.util.ServiceLoader.Provider::get)
                .filter(candidate -> candidate.packageBuilder().outputType().equals("winget"))
                .findFirst().orElseThrow();
        assertInstanceOf(WingetRepository.class, repository);
    }

    private PackageConfig config(String name, String version, Arch arch) throws Exception {
        return YAML.readValue("""
                meta:
                  name: %s
                  version: %s
                  arch: %s
                control:
                  maintainer: Example Publisher
                  description: Small portable executable
                files:
                  controlFiles: []
                  dataFiles:
                  - type: binary
                    path: bin/example-hello.exe
                    mode: 0x755
                    content: SGVsbG8gZnJvbSBhIHRlc3QgUEUK
                """.formatted(name, version, arch), PackageConfig.class);
    }

    private List<String> zipEntries(byte[] archive) throws Exception {
        var result = new java.util.ArrayList<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) result.add(entry.getName());
        }
        return result;
    }

    private byte[] zipEntry(byte[] archive, String name) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (name.equals(entry.getName())) return zip.readAllBytes();
        }
        throw new AssertionError("missing ZIP entry " + name);
    }

    private String query(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
