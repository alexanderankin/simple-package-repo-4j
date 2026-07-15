package simple.repo.deb;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import simple.repo.keys.KeysUtils;
import simple.repo.model.FileIntegrity;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.IndexFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Data
@Accessors(chain = true)
public class DebRepoBuilder {
    static final DateTimeFormatter RELEASE_DATE = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .withZone(ZoneOffset.UTC);
    private DebPackageBuilder debPackageBuilder = new DebPackageBuilder();

    public RepoBuilder repoBuilder(RepoConfig config) {
        return new RepoBuilder(config, Instant.now());
    }

    public RepoBuilder repoBuilder(RepoConfig config, Instant now) {
        return new RepoBuilder(config, now);
    }

    public Map<String, FileIntegrityWithContent> buildRepo(Repo repo) {
        return repo.codenameSections().values().stream()
                .flatMap(section -> section.packagesFiles().entrySet().stream()
                        .map(file -> Map.entry(section.getCodename() + "/" + file.getKey(), file.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String buildRelease(RepoConfig config, Repo.CodenameSection section) {
        return release(config, section);
    }

    public Map<String, FileIntegrityWithContent> signRepo(Map<String, FileIntegrityWithContent> repoFiles,
                                                          byte[] privateKey,
                                                          byte[] publicKey,
                                                          Instant now) {
        var signed = new HashMap<String, FileIntegrityWithContent>();
        for (var release : repoFiles.entrySet()) {
            if (!release.getKey().endsWith("/Release")) {
                continue;
            }
            var directory = release.getKey().substring(0, release.getKey().length() - "Release".length());
            var content = release.getValue().getContent();
            signed.put(directory + "InRelease", FileIntegrityWithContent.of(
                    KeysUtils.generateClearSigned(privateKey, content, now, null), "InRelease"));
            signed.put(directory + "Release.gpg", FileIntegrityWithContent.of(
                    KeysUtils.generateDetachedSig(privateKey, content, now, null), "Release.gpg"));
            signed.put(directory + "repository.asc", FileIntegrityWithContent.of(publicKey, "repository.asc"));
        }
        return signed;
    }

    private String packagesIndex(String codename, List<IndexFile> packages) {
        return packages.stream().map(meta -> packageEntry(codename, meta)).collect(Collectors.joining("\n"));
    }

    private String packageEntry(String codename, IndexFile packageMeta) {
        var config = packageMeta.getPackageConfig();
        var meta = config.getMeta();
        var control = config.getControl();
        var integrity = packageMeta.getFileIntegrity();
        var out = new StringBuilder()
                .append("Package: ").append(meta.getName()).append('\n')
                .append("Version: ").append(meta.getVersion()).append('\n')
                .append("Architecture: ").append(debPackageBuilder.archName(meta.getArch())).append('\n')
                .append("Maintainer: ").append(control.getMaintainer()).append('\n');
        appendOptional(out, "Depends", control.getDepends());
        appendOptional(out, "Conflicts", control.getConflicts());
        appendOptional(out, "Recommends", control.getRecommends());
        out.append("Filename: pool/").append(codename).append('/').append(integrity.getPath()).append('\n');
        if (control.getInstalledSize() != null) {
            out.append("Installed-Size: ").append(control.getInstalledSize()).append('\n');
        }
        out.append("Size: ").append(integrity.getSize()).append('\n')
                .append("MD5sum: ").append(integrity.getMd5()).append('\n')
                .append("SHA1: ").append(integrity.getSha1()).append('\n')
                .append("SHA256: ").append(integrity.getSha256()).append('\n')
                .append("SHA512: ").append(integrity.getSha512()).append('\n')
                .append("Section: ").append(control.getSection()).append('\n')
                .append("Priority: ").append(control.getPriority()).append('\n');
        appendOptional(out, "Homepage", control.getHomepage());
        return out.append("Description: ").append(control.getDescription()).append('\n').toString();
    }

    private void appendOptional(StringBuilder out, String name, String value) {
        if (value != null && !value.isBlank()) {
            out.append(name).append(": ").append(value).append('\n');
        }
    }

    private String release(RepoConfig config, Repo.CodenameSection section) {
        var header = """
                Origin: %s
                Label: %s
                Suite: %s
                Codename: %s
                Architectures: %s
                Components: %s
                Date: %s
                Description: Repository for %s
                """.formatted(
                Objects.requireNonNullElse(config.getOrigin(), section.getCodename()),
                Objects.requireNonNullElse(config.getLabel(), section.getCodename()),
                section.getCodename(),
                section.getCodename(),
                section.arches().stream().sorted().collect(Collectors.joining(" ")),
                section.components().stream().sorted().collect(Collectors.joining(" ")),
                RELEASE_DATE.format(section.getDate()),
                section.getCodename());
        section.packagesFiles().put("Release", FileIntegrityWithContent.of(header, "Release"));
        var release = header
                + hashSection("MD5Sum", FileIntegrity::getMd5, section.packagesFiles()) + '\n'
                + hashSection("SHA1", FileIntegrity::getSha1, section.packagesFiles()) + '\n'
                + hashSection("SHA256", FileIntegrity::getSha256, section.packagesFiles()) + '\n'
                + hashSection("SHA512", FileIntegrity::getSha512, section.packagesFiles()) + '\n';
        section.packagesFiles().put("Release", FileIntegrityWithContent.of(release, "Release"));
        return release;
    }

    private String hashSection(String name,
                               Function<FileIntegrity, String> hash,
                               Map<String, FileIntegrityWithContent> files) {
        return name + ":\n" + files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(file -> " " + hash.apply(file.getValue().getFileIntegrity())
                        + " " + String.format("%16s", file.getValue().getFileIntegrity().getSize())
                        + " " + file.getKey())
                .collect(Collectors.joining("\n"));
    }

    @SneakyThrows
    private byte[] gzip(byte[] content) {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(content);
        }
        return out.toByteArray();
    }

    @Data
    @Accessors(chain = true)
    public static class RepoConfig {
        String origin;
        String label;
    }

    @Data
    @Accessors(chain = true)
    public static class Repo {
        Map<String, CodenameSection> codenameSections;

        Map<String, CodenameSection> codenameSections() {
            if (codenameSections == null) codenameSections = new LinkedHashMap<>();
            return codenameSections;
        }

        @Data
        @Accessors(chain = true)
        public static class CodenameSection {
            final String codename;
            Set<String> arches;
            Set<String> components;
            Instant date;
            Map<String, FileIntegrityWithContent> packagesFiles;

            Set<String> arches() {
                if (arches == null) arches = new TreeSet<>();
                return arches;
            }

            Set<String> components() {
                if (components == null) components = new TreeSet<>();
                return components;
            }

            Map<String, FileIntegrityWithContent> packagesFiles() {
                if (packagesFiles == null) packagesFiles = new HashMap<>();
                return packagesFiles;
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

        public CodenameSectionBuilder buildCodename(String codename) {
            var section = repo.codenameSections().computeIfAbsent(codename, Repo.CodenameSection::new);
            section.setDate(now);
            return new CodenameSectionBuilder(this, section);
        }

        public Repo build() {
            return repo;
        }
    }

    @RequiredArgsConstructor
    public class CodenameSectionBuilder {
        @NonNull
        final RepoBuilder repoBuilder;
        @NonNull
        final Repo.CodenameSection section;
        final List<IndexFile> packages = new ArrayList<>();

        public CodenameSectionBuilder addPackage(IndexFile packageMeta) {
            var config = packageMeta.getPackageConfig();
            section.arches().add(debPackageBuilder.archName(config.getMeta().getArch()));
            section.components().add(config.getControl().getSection());
            packages.add(packageMeta);
            return this;
        }

        public RepoBuilder build() {
            for (var component : section.components()) {
                for (var arch : section.arches()) {
                    var matching = packages.stream()
                            .filter(meta -> component.equals(meta.getPackageConfig().getControl().getSection()))
                            .filter(meta -> arch.equals(debPackageBuilder.archName(meta.getPackageConfig().getMeta().getArch())))
                            .toList();
                    var path = component + "/binary-" + arch + "/Packages";
                    var content = packagesIndex(section.getCodename(), matching).getBytes(StandardCharsets.UTF_8);
                    section.packagesFiles().put(path, FileIntegrityWithContent.of(content, path));
                    section.packagesFiles().put(path + ".gz", FileIntegrityWithContent.of(gzip(content), path + ".gz"));
                }
            }
            release(repoBuilder.config, section);
            return repoBuilder;
        }
    }
}
