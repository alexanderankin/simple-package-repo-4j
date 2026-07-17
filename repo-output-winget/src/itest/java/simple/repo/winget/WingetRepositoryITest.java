package simple.repo.winget;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import simple.repo.model.Arch;
import simple.repo.model.IndexFile;
import simple.repo.model.PackageConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for a signed source.msix served by an otherwise plain static file server. */
public class WingetRepositoryITest {
    @Test
    @SneakyThrows
    void installsPortableExecutableByNameFromStaticRepository(@TempDir Path temporary) {
        var dockerInfo = DockerClientFactory.instance().client().infoCmd().exec();
        Assumptions.assumeTrue("windows".equalsIgnoreCase(dockerInfo.getOsType()),
                "requires a Docker daemon configured for Windows containers");

        var executable = temporary.resolve("example-winget.exe");
        var compileFixture = """
                $source = 'using System; public class Program { public static void Main() { Console.WriteLine("example-winget"); } }'
                Add-Type -TypeDefinition $source -OutputAssembly '%s' -OutputType ConsoleApplication
                """.formatted(executable.toAbsolutePath().toString().replace("'", "''"));
        assertSuccess(run("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", compileFixture));

        var config = new PackageConfig()
                .setMeta(new PackageConfig.PackageMeta().setName("SimpleRepo.ExampleWinget")
                        .setVersion("1.0.0").setArch(Arch.amd64))
                .setControl(new PackageConfig.ControlExtras().setMaintainer("Simple Repo")
                        .setDescription("example-winget portable test executable"))
                .setFiles(new PackageConfig.FileSpec().setControlFiles(List.of()).setDataFiles(List.of(
                        new PackageConfig.PkgFileSpec.FilePkgFileSpec().setSourcePath(executable.toString())
                                .setPath("bin/example-winget.exe").setMode(0x755))));
        var packageBuilder = new WingetPackageBuilder();
        var portableZip = packageBuilder.buildPackage(config);
        var index = new IndexFile().setPackageConfig(config).setFileIntegrity(portableZip.getFileIntegrity());
        var pfx = temporary.resolve("source-signing.pfx");
        var cer = temporary.resolve("source-signing.cer");
        WingetCertificateCli.generate("CN=Simple Repo", pfx, cer, null, 0xE42, 0x800, false);
        var privateKey = Files.readAllBytes(pfx);
        var publicKey = Files.readAllBytes(cer);
        var builder = new WingetRepoBuilder().setPackageBuilder(packageBuilder)
                .setBaseUrl("http://127.0.0.1:8080/");
        builder.prepareSigning(privateKey, publicKey);
        var now = Instant.now();
        var repository = builder.build(Map.of(RepoOutputWinget.TARGET, List.of(index)), now);
        repository.putAll(builder.sign(repository, privateKey, publicKey, now));

        try (var container = windowsContainer()) {
            repository.forEach((path, file) -> container.withCopyToContainer(
                    Transferable.of(file.getContent()), "C:\\repo\\" + path.replace('/', '\\')));
            container.withCopyToContainer(Transferable.of(portableZip.getContent()),
                    "C:\\repo\\packages\\" + portableZip.getFileIntegrity().getPath());
            container.withCopyToContainer(Transferable.of(resource("serve-static.ps1")), "C:\\repo\\serve-static.ps1");
            container.start();

            assertSuccess(container.execInContainer("powershell.exe", "-NoProfile", "-Command", trustSourceScript()));
            assertSuccess(container.execInContainer("powershell.exe", "-NoProfile", "-Command",
                    "Start-Process powershell.exe -ArgumentList '-NoProfile -File C:\\repo\\serve-static.ps1' -WindowStyle Hidden"));
            assertSuccess(container.execInContainer("powershell.exe", "-NoProfile", "-Command", installWingetScript()));

            var winget = "C:\\winget\\winget.exe";
            for (var stock : List.of("winget", "msstore", "winget-font"))
                container.execInContainer(winget, "source", "remove", stock, "--disable-interactivity");
            assertSuccess(container.execInContainer(winget, "source", "add", "--name", "simple-repo",
                    "--arg", "http://127.0.0.1:8080/", "--type", "Microsoft.PreIndexed.Package",
                    "--trust-level", "trusted", "--accept-source-agreements", "--disable-interactivity"));
            assertSuccess(container.execInContainer(winget, "install", "--id", "SimpleRepo.ExampleWinget",
                    "--source", "simple-repo", "--exact", "--accept-package-agreements",
                    "--accept-source-agreements", "--disable-interactivity"));
            var invocation = container.execInContainer("cmd.exe", "/S", "/C", "example-winget.exe");
            assertSuccess(invocation);
            assertEquals("example-winget", invocation.getStdout().strip());
        }
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> windowsContainer() {
        return new GenericContainer<>("mcr.microsoft.com/windows/server:ltsc2025")
                .withCreateContainerCmdModifier(command -> command.withEntrypoint(
                        "cmd.exe", "/S", "/C", "ping -t 127.0.0.1 > NUL"));
    }

    private String trustSourceScript() {
        return """
                $ErrorActionPreference = 'Stop'
                Import-Certificate C:\\repo\\simple-repo-winget.cer -CertStoreLocation Cert:\\LocalMachine\\TrustedPeople | Out-Null
                """;
    }

    private String installWingetScript() {
        return """
                $ErrorActionPreference = 'Stop'
                [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
                Invoke-WebRequest 'https://github.com/microsoft/winget-cli/releases/latest/download/Microsoft.DesktopAppInstaller.WithDependencies.zip' -OutFile C:\\winget.zip
                Expand-Archive C:\\winget.zip C:\\winget-release
                $bundle = Get-ChildItem C:\\winget-release -Filter *.msixbundle -Recurse | Select-Object -First 1
                $dependencies = Get-ChildItem C:\\winget-release -Include *.appx,*.msix -Recurse | ForEach-Object FullName
                Add-AppxPackage -Path $bundle.FullName -DependencyPath $dependencies
                $location = (Get-AppxPackage Microsoft.DesktopAppInstaller).InstallLocation
                New-Item -ItemType Directory C:\\winget -Force | Out-Null
                Copy-Item (Join-Path $location '*') C:\\winget -Recurse -Force
                """;
    }

    private byte[] resource(String name) throws Exception {
        try (var input = getClass().getResourceAsStream(name)) {
            return java.util.Objects.requireNonNull(input).readAllBytes();
        }
    }

    private ProcessResult run(String... command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private void assertSuccess(ProcessResult result) {
        assertEquals(0, result.exitCode, result.output);
    }

    private void assertSuccess(Container.ExecResult result) {
        assertEquals(0, result.getExitCode(), () -> result.getStdout() + "\n" + result.getStderr());
    }

    private record ProcessResult(int exitCode, String output) {}
}
