package simple.repo.deb;

import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import simple.repo.model.PackageConfig;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebParsePackageConfig {
    static final DebParsePackageConfig INSTANCE = new DebParsePackageConfig();

    @SneakyThrows
    public PackageConfig parseConfigFromPackage(byte[] downloadedPackage) {
        byte[] controlTar = null;
        byte[] dataTar = null;

        try (var ar = new ArArchiveInputStream(new ByteArrayInputStream(downloadedPackage))) {
            ArArchiveEntry entry;
            while ((entry = ar.getNextEntry()) != null) {
                var name = entry.getName();

                if (name.startsWith("control.tar")) {
                    controlTar = ar.readAllBytes();
                } else if (name.startsWith("data.tar")) {
                    dataTar = ar.readAllBytes();
                }
            }
        }

        if (controlTar == null) {
            throw new IllegalArgumentException("Invalid .deb: missing control.tar.*");
        }

        var controlFiles = new ArrayList<PackageConfig.TarFileSpec>();
        var dataFiles = new ArrayList<PackageConfig.TarFileSpec>();

        String controlText = null;

        try (var tar = openTar(controlTar)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                var path = normalizeTarPath(entry.getName());

                if ("control".equals(path)) {
                    controlText = new String(tar.readAllBytes(), StandardCharsets.UTF_8);
                    continue;
                }

                controlFiles.add(new PackageConfig.TarFileSpec.FileTarFileSpec()
                        // .setContent(new String(tar.readAllBytes(), StandardCharsets.UTF_8))
                        .setMode(entry.getMode())
                        .setPath(entry.getName()));
            }
        }

        if (controlText == null) {
            throw new IllegalArgumentException("Invalid .deb: missing control file");
        }

        if (dataTar != null) {
            try (var tar = openTar(dataTar)) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    var debPath = "/" + normalizeTarPath(entry.getName());

                    dataFiles.add(new PackageConfig.TarFileSpec.FileTarFileSpec()
                            .setPath(debPath)
                            .setMode(entry.getMode()));
                }
            }
        }

        var fields = parseDebControl(controlText);

        var meta = new PackageConfig.PackageMeta()
                .setName(fields.get("Package"))
                .setVersion(fields.get("Version"))
                .setArch(DebArch.valueOf(fields.get("Architecture")).getArch());

        var control = new PackageConfig.ControlExtras()
                .setDepends(fields.getOrDefault("Depends", ""))
                .setRecommends(fields.getOrDefault("Recommends", ""))
                .setSection(fields.getOrDefault("Section", "main"))
                .setPriority(fields.getOrDefault("Priority", "optional"))
                .setHomepage(fields.getOrDefault("Homepage", ""))
                .setConflicts(fields.getOrDefault("Conflicts", ""))
                .setMaintainer(fields.getOrDefault("Maintainer", ""))
                .setDescription(fields.getOrDefault("Description", ""));

        if (fields.containsKey("Installed-Size")) {
            control.setInstalledSize(Integer.valueOf(fields.get("Installed-Size")));
        }

        return new PackageConfig()
                .setMeta(meta)
                .setControl(control)
                .setFiles(new PackageConfig.FileSpec()
                        .setControlFiles(controlFiles)
                        .setDataFiles(dataFiles));
    }

    @SneakyThrows
    private TarArchiveInputStream openTar(byte[] compressedTar) {
        var in = new BufferedInputStream(new ByteArrayInputStream(compressedTar));
        var compressor = new CompressorStreamFactory()
                .createCompressorInputStream(in);
        return new TarArchiveInputStream(compressor);
    }

    private String normalizeTarPath(String path) {
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path;
    }

    private Map<String, String> parseDebControl(String text) {
        var fields = new LinkedHashMap<String, String>();
        String currentKey = null;

        for (String line : text.split("\\R", -1)) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (currentKey != null) {
                    fields.put(currentKey, fields.get(currentKey) + "\n" + line.substring(1));
                }
                continue;
            }

            var colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }

            currentKey = line.substring(0, colon);
            fields.put(currentKey, line.substring(colon + 1).trim());
        }

        return fields;
    }
}
