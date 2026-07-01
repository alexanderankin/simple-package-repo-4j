package simple.repo.cli;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.AutoComplete;
import picocli.CommandLine;
import simple.repo.cli.util.Exists;
import simple.repo.keys.KeysUtils;
import simple.repo.keys.SupportedKeyGenerationProfile;

import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@CommandLine.Command(
        name = "simple-repo",
        description = "simple linux packaging and repository utility",
        versionProvider = ManifestVersionProvider.class,
        mixinStandardHelpOptions = true,
        sortOptions = false,
        showDefaultValues = true,
        scope = CommandLine.ScopeType.INHERIT,
        subcommands = {
                SimpleRepoApplication.Keys.class,
                SimpleRepoApplication.Package.class,
                SimpleRepoApplication.Repo.class,
                AutoComplete.GenerateCompletion.class,
        }
)
public class SimpleRepoApplication {
    static void main(String[] args) {
        var commandLine = new CommandLine(SimpleRepoApplication.class)
                .setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    static SimpleRepoCli instance() {
        return new SimpleRepoCli();
    }

    @CommandLine.Command(name = "package", subcommands = {
            Package.Build.class,
            Package.IndexFile.class,
    })
    public static class Package {
        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "build")
        public static class Build implements Runnable {
            @NotNull
            @Exists
            @CommandLine.Option(names = {"-c", "--config"})
            Path configFile;

            @Override
            public void run() {
                instance().validate("build args", this).buildPackage(this);
            }
        }
        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "index", aliases = {"index-file"})
        public static class IndexFile {
            @NotNull
            @Exists
            @CommandLine.Parameters(arity = "1..")
            List<Path> packages;
        }
    }

    @CommandLine.Command(name = "repo", subcommands = {
            Repo.IndexFile.class,
            Repo.Publish.class,
    })
    public static class Repo {
        @CommandLine.Option(names = {"-r", "--repo"}, description = """
                provide a way to find the repository
                
                s3://bucket/path/to/repository
                file:///abs/path/to/repository
                file:./relative-path-to-repository
                """)
        URI repoBase;

        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "index", aliases = {"index-file"})
        public static class IndexFile implements Runnable {
            @Valid
            @CommandLine.ParentCommand
            Repo repo;

            @CommandLine.Parameters(arity = "1..")
            List<String> packages;

            @Override
            public void run() {
                var instance = instance();
                for (String eachPackage : packages) {
                    instance.indexExistingRepoFile(eachPackage);
                }
            }
        }

        @CommandLine.Command(name = "publish")
        public static class Publish {

        }
    }

    @CommandLine.Command(name = "keys", subcommands = {
            Keys.Gen.class,
            Keys.Sign.class,
            Keys.Verify.class,
    })
    public static class Keys {
        @CommandLine.Command(name = "gen")
        public static class Gen implements Runnable {
            @CommandLine.Option(names = {"-n", "--name"})
            String name;
            @CommandLine.Option(names = {"-e", "--email"})
            String email;
            @CommandLine.Option(names = {"-p", "--profile"})
            SupportedKeyGenerationProfile profile;
            @CommandLine.ArgGroup
            KeyOutput keyOutput;

            @SneakyThrows
            @Override
            public void run() {
                var keyrings = KeysUtils.genKeyPairKeyring(name, email, profile);

                if (keyOutput == null) {
                    System.out.println(keyrings.getPublicKey());
                    System.out.println(keyrings.getPrivateKey());
                } else if (keyOutput.outputBoth != null) {
                    try (var out = Files.newOutputStream(keyOutput.outputBoth);
                         var writer = new PrintWriter(out)) {
                        writer.println(keyrings.getPublicKey());
                        writer.println(keyrings.getPrivateKey());
                    }
                } else if (keyOutput.outputSeparate != null) {
                    Files.writeString(keyOutput.outputSeparate.outputPublic, keyrings.getPublicKey());
                    Files.writeString(keyOutput.outputSeparate.outputPrivate, keyrings.getPrivateKey());
                }
            }

            @Data
            @Accessors(chain = true)
            public static class KeyOutput {
                @CommandLine.Option(names = {"-o", "--out"}, description = "output both parts to same file")
                Path outputBoth;
                @CommandLine.ArgGroup(exclusive = false)
                KeyOutputSeparate outputSeparate;

                @Data
                @Accessors(chain = true)
                static class KeyOutputSeparate {
                    @CommandLine.Option(names = {"-op", "--out-public"}, description = "output public part to file")
                    Path outputPublic;
                    @CommandLine.Option(names = {"-os", "--out-secret"}, description = "output secret part to file")
                    Path outputPrivate;
                }
            }
        }

        @CommandLine.Command(name = "sign")
        public static class Sign {
        }

        @CommandLine.Command(name = "verify")
        public static class Verify {
        }
    }
}
