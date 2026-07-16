package simple.repo.cli;

import info.ankin.projects.picocli.logback.verbosity.LogbackVerbosityMixin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.AutoComplete;
import picocli.CommandLine;
import simple.repo.cli.util.Exists;
import simple.repo.io.RepoIo;
import simple.repo.keys.KeysUtils;
import simple.repo.keys.SupportedKeyGenerationProfile;
import simple.repo.repository.Repository;
import simple.repo.repository.RepositoryInitialization;

import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
                SimpleRepoApplication.Plugins.class,
                AutoComplete.GenerateCompletion.class,
        }
)
public class SimpleRepoApplication {
    @CommandLine.Mixin
    LogbackVerbosityMixin logbackVerbosityMixin;

    static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    static CommandLine commandLine() {
        return new CommandLine(SimpleRepoApplication.class)
                .setCaseInsensitiveEnumValuesAllowed(true);
    }

    static SimpleRepoCli instance() {
        return new SimpleRepoCli();
    }

    @Data
    @Accessors(chain = true)
    @CommandLine.Command(name = "package", description = "", subcommands = {
            Package.Build.class,
            Package.IndexFile.class,
    })
    public static class Package {
        @CommandLine.Option(names = {"-t", "--type", "--output-type"},
                required = true,
                description = "output package type (e.g. deb, rpm, pacman)")
        String repoType;

        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "build", description = "")
        public static class Build implements Runnable {
            @Valid
            @CommandLine.ParentCommand
            Package repo;

            @NotNull
            @Exists
            @CommandLine.Option(names = {"-c", "--config"}, required = true)
            Path configFile;

            @CommandLine.ArgGroup(multiplicity = "1")
            BuildOutput buildOutput;

            @Override
            public void run() {
                var result = instance().validate("build args", this).buildPackage(this);
                System.out.println(result);
            }

            @Data
            @Accessors(chain = true)
            public static class BuildOutput {
                @CommandLine.Option(names = {"-o", "--output"})
                Path outputDir;

                @CommandLine.Option(names = {"-f", "--file"})
                Path outputFile;

            }
        }

        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "index", description = "", aliases = {"index-file"})
        public static class IndexFile implements Runnable {
            @Valid
            @CommandLine.ParentCommand
            Package repo;

            @NotNull
            @Valid
            @CommandLine.Parameters(arity = "1..")
            List<@Exists Path> packages;

            @Override
            public void run() {
                instance().validate("index subcommand args", this).indexFiles(repo.getRepoType(), packages);
            }
        }
    }

    @Data
    @Accessors(chain = true)
    @CommandLine.Command(name = "repo", description = "", subcommands = {
            Repo.IndexFile.class,
            Repo.Scan.class,
            Repo.Add.class,
    })
    public static class Repo {
        @CommandLine.Option(names = {"-t", "--type", "--repo-type"},
                required = true,
                description = "repository package type (e.g. deb, rpm, pacman)")
        String repoType;

        @CommandLine.Option(names = {"-r", "--repo"},
                required = true,
                description = """
                        provide a way to find the repository
                        
                        s3://bucket/path/to/repository
                        file:///abs/path/to/repository
                        file:./relative-path-to-repository
                        """)
        URI repoBase;

        @CommandLine.Option(names = "--target", description = "which partitions to target with the operation (deb: codenames, rpm: el versions)")
        List<String> targets;

        @CommandLine.Option(names = "--keep-versions", description = "latest versions to publish per package and architecture")
        Integer keepVersions;

        @Exists
        @CommandLine.Option(names = {"-P", "--public-key"}, description = "repository signing public key")
        Path publicKey;

        @Exists
        @CommandLine.Option(names = {"-S", "--secret-key"}, description = "repository signing secret key")
        Path secretKey;

        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "index", description = "", aliases = {"index-file"})
        public static class IndexFile implements Runnable {
            @Valid
            @CommandLine.ParentCommand
            Repo repo;

            @CommandLine.Parameters(arity = "1..")
            List<String> packages;

            @Override
            public void run() {
                var instance = instance();
                RepoIo<?> repoIo = instance.loadRepoIo(repo.getRepoBase());
                Repository<?> repository = instance.loadRepo(repo.getRepoType());
                instance.indexRepositoryPackages(repoIo, repository, packages);
            }
        }

        @CommandLine.Command(name = "scan", description = "scan pool, update")
        public static class Scan implements Runnable {
            @Valid
            @CommandLine.ParentCommand
            Repo repo;

            @Override
            public void run() {
                var instance = instance();
                RepoIo<?> repoIo = instance.loadRepoIo(repo.getRepoBase());
                Repository<?> repository = instance.loadRepo(repo.getRepoType());
                instance.rebuildRepository(repoIo, repository, repo);
            }
        }

        @CommandLine.Command(name = "add", description = "add packages to a repository")
        public static class Add implements Runnable {
            @Valid
            @CommandLine.ParentCommand
            Repo repo;

            @CommandLine.ArgGroup(multiplicity = "1")
            AddSource source;

            @CommandLine.Option(names = "--init",
                    description = "empty repo policy: init (fails if !empty), allowed (if needed), re_init (always), disabled (fails if empty)")
            RepositoryInitialization initialization = RepositoryInitialization.disabled;

            @Override
            public void run() {
                var instance = instance();
                RepoIo<?> repoIo = instance.loadRepoIo(repo.getRepoBase());
                Repository<?> repository = instance.loadRepo(repo.getRepoType());
                add(instance, repoIo, repository);
            }

            private <I extends RepoIo.RepoLocation, R> void add(SimpleRepoCli instance,
                                                                RepoIo<I> repoIo,
                                                                Repository<R> repository) {
                if (source.config != null) {
                    instance.addPackageConfigs(repoIo, repository, source.config, this);
                } else {
                    instance.addRepositoryIndexes(repoIo, repository, source.indexes, repo.keepVersions,
                            initialization, repo.publicKey, repo.secretKey);
                }
            }

            @Data
            static class AddSource {
                @Valid
                @CommandLine.Option(names = {"-c", "--config"}, arity = "1..",
                        description = "package configurations to build and add")
                List<@Exists Path> config;

                @CommandLine.Option(names = {"-i", "--index"}, arity = "1..",
                        description = "repository coordinates of sidecar index files")
                List<String> indexes;
            }
        }
    }

    @CommandLine.Command(name = "keys", description = "", subcommands = {
            Keys.Gen.class,
            Keys.Sign.class,
            Keys.Verify.class,
    })
    public static class Keys {
        @CommandLine.Command(name = "gen", description = "")
        public static class Gen implements Runnable {
            @CommandLine.Spec
            CommandLine.Model.CommandSpec spec;

            @CommandLine.Option(names = {"-n", "--name"})
            String name;
            @CommandLine.Option(names = {"-e", "--email"})
            String email;
            @CommandLine.Option(names = {"-p", "--profile"})
            SupportedKeyGenerationProfile profile = SupportedKeyGenerationProfile.CURVE25519;
            @CommandLine.ArgGroup
            KeyOutput keyOutput;

            @SneakyThrows
            @Override
            public void run() {
                var keyrings = KeysUtils.genKeyPairKeyring(name, email, profile);

                if (keyOutput == null) {
                    spec.commandLine().getOut().println(keyrings.getPublicKey());
                    spec.commandLine().getOut().println(keyrings.getPrivateKey());
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
                @CommandLine.Option(names = {"-o", "-O", "--out"}, description = "output both parts to same file")
                Path outputBoth;
                @CommandLine.ArgGroup(exclusive = false)
                KeyOutputSeparate outputSeparate;

                @Data
                @Accessors(chain = true)
                static class KeyOutputSeparate {
                    @CommandLine.Option(names = {"-P", "--out-public"}, description = "output public part to file")
                    Path outputPublic;
                    @CommandLine.Option(names = {"-S", "--out-secret"}, description = "output secret part to file")
                    Path outputPrivate;
                }
            }
        }

        @CommandLine.Command(name = "sign", description = "")
        public static class Sign {
            @CommandLine.Spec
            CommandLine.Model.CommandSpec spec;

            @SneakyThrows
            @CommandLine.Command(name = "clear", aliases = {"clear-sign", "inline"}, description = "")
            void clearSign(@CommandLine.Mixin SignArgs a) {
                instance().validate(null, this);
                output(a, KeysUtils.generateClearSigned(Files.readAllBytes(a.secretKey), Files.readAllBytes(a.inputFile), a.now, null));
            }

            @SneakyThrows
            @CommandLine.Command(name = "detached", aliases = {"detached-sign"}, description = "")
            void detachedSign(@CommandLine.Mixin SignArgs a) {
                instance().validate(null, this);
                output(a, KeysUtils.generateDetachedSig(Files.readAllBytes(a.secretKey), Files.readAllBytes(a.inputFile), a.now, null));
            }

            @SneakyThrows
            private void output(SignArgs a, byte[] bytes) {
                if (a.outputFile != null) {
                    Files.write(a.outputFile, bytes);
                } else {
                    spec.commandLine().getOut().println(new String(bytes, StandardCharsets.UTF_8));
                }
            }

            @Data
            @Accessors(chain = true)
            static class SignArgs {
                @CommandLine.Option(names = {"-i", "--input"}, required = true)
                @Exists
                Path inputFile;

                @CommandLine.Option(names = {"-S", "--secret-key"}, required = true)
                @Exists
                Path secretKey;

                @CommandLine.Option(names = {"-t", "--timestamp", "--now"}, description = "timestamp to use for signature, default: now")
                Instant now = Instant.now();

                @CommandLine.Option(names = {"-o", "--output"}, description = "where to place the signature (if not stdout)")
                Path outputFile;
            }
        }

        @Data
        @Accessors(chain = true)
        @CommandLine.Command(name = "verify", description = "")
        public static class Verify {
            @CommandLine.Spec
            CommandLine.Model.CommandSpec spec;

            @SneakyThrows
            @CommandLine.Command(name = "clear", aliases = {"clear-sign", "inline"}, description = "")
            void clear(@CommandLine.Mixin VerifyArgs a) {
                var instance = instance();
                instance.validate(null, a);
                var r = KeysUtils.verifySignatureInline(Files.readAllBytes(a.publicKey), Files.readAllBytes(a.inputFile));
                spec.commandLine().getOut().println(instance.jsonMapper.writeValueAsString(r));
            }

            @SneakyThrows
            @CommandLine.Command(name = "detached", aliases = {"detached-sign"}, description = "")
            void detached(@CommandLine.Mixin VerifyArgs a,
                          @CommandLine.Option(names = {"-d", "--data", "--data-file"}) Path data) {
                var instance = instance();
                instance.validate(null, a);
                var r = KeysUtils.verifySignatureDetached(
                        Files.readAllBytes(a.publicKey),
                        Files.readAllBytes(a.inputFile),
                        Files.readAllBytes(data)
                );
                spec.commandLine().getOut().println(instance.jsonMapper.writeValueAsString(r));
            }

            @Data
            @Accessors(chain = true)
            static class VerifyArgs {
                @CommandLine.Option(names = {"-i", "--input"}, required = true)
                @Exists
                Path inputFile;

                @CommandLine.Option(names = {"-P", "--public-key"}, required = true)
                @Exists
                Path publicKey;
            }
        }
    }

    @CommandLine.Command(name = "plugins", description = "", subcommands = {
            Plugins.PluginsList.class,
    })
    public static class Plugins {
        @CommandLine.Command(name = "list", description = "")
        public static class PluginsList {
            @CommandLine.Command(name = "io", description = "")
            void io() {
                SimpleRepoCli instance = instance();
                var repoIos = instance.loadedRepoIos();
                var repoIosNames = repoIos.stream().map(Object::getClass).map(Class::getSimpleName).toList();
                System.out.println(instance.jsonMapper.writeValueAsString(repoIosNames));
            }

            @CommandLine.Command(name = "output", description = "")
            void output() {
                var instance = instance();
                var repos = instance.loadedRepos();
                var reposNames = repos.keySet().stream().sorted().toList();
                System.out.println(instance.jsonMapper.writeValueAsString(reposNames));
            }
        }
    }
}
