package simple.repo.winget;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the unsigned MSIX source package that {@link WingetRepoBuilder} signs during publication. */
public final class WingetMsixBuilder {
    private static final int BLOCK_SIZE = 0x10000;
    private static final byte[] LOGO = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @SneakyThrows
    public byte[] build(byte[] index, Instant now, String identityName, String publisher) {
        var files = new LinkedHashMap<String, byte[]>();
        files.put("Assets/Logo.png", LOGO);
        files.put("Public/index.db", index);
        files.put("AppxManifest.xml", manifest(now, identityName, publisher).getBytes(StandardCharsets.UTF_8));
        var blockMap = blockMap(files).getBytes(StandardCharsets.UTF_8);
        var contentTypes = contentTypes().getBytes(StandardCharsets.UTF_8);

        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : files.entrySet()) putStored(zip, entry.getKey(), entry.getValue());
            putStored(zip, "AppxBlockMap.xml", blockMap);
            putStored(zip, "[Content_Types].xml", contentTypes);
        }
        return output.toByteArray();
    }

    private String manifest(Instant now, String identityName, String publisher) {
        var date = now.atZone(ZoneOffset.UTC);
        var version = "%d.%d.%d.%d".formatted(date.getYear(), date.getMonthValue() * 100 + date.getDayOfMonth(),
                date.getHour() * 100 + date.getMinute(), date.getSecond());
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                         xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
                         xmlns:uap3="http://schemas.microsoft.com/appx/manifest/uap/windows10/3"
                         IgnorableNamespaces="uap uap3">
                  <Identity Name="%s" ProcessorArchitecture="neutral" Publisher="%s" Version="%s" />
                  <Properties>
                    <DisplayName>Simple Package Repository WinGet Source</DisplayName>
                    <PublisherDisplayName>Simple Package Repository</PublisherDisplayName>
                    <Logo>Assets\\Logo.png</Logo>
                  </Properties>
                  <Dependencies><TargetDeviceFamily Name="Windows.Universal" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" /></Dependencies>
                  <Applications>
                    <Application Id="SourceData">
                      <uap:VisualElements DisplayName="Simple Package Repository" Square150x150Logo="Assets\\Logo.png"
                          Square44x44Logo="Assets\\Logo.png" Description="Simple Package Repository" BackgroundColor="transparent" AppListEntry="none" />
                      <Extensions><uap3:Extension Category="windows.appExtension"><uap3:AppExtension
                          Name="com.microsoft.winget.source" Id="IndexDB" DisplayName="Simple Package Repository"
                          Description="Simple Package Repository" PublicFolder="Public" /></uap3:Extension></Extensions>
                    </Application>
                  </Applications>
                  <Resources><Resource Language="und" /></Resources>
                </Package>
                """.formatted(xml(identityName), xml(publisher), version);
    }

    @SneakyThrows
    private String blockMap(Map<String, byte[]> files) {
        var result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>")
                .append("<BlockMap xmlns=\"http://schemas.microsoft.com/appx/2010/blockmap\" HashMethod=\"http://www.w3.org/2001/04/xmlenc#sha256\">");
        var digest = MessageDigest.getInstance("SHA-256");
        for (var file : files.entrySet()) {
            var nameBytes = file.getKey().getBytes(StandardCharsets.UTF_8);
            result.append("<File Name=\"").append(xml(file.getKey().replace('/', '\\')))
                    .append("\" Size=\"").append(file.getValue().length)
                    .append("\" LfhSize=\"").append(30 + nameBytes.length).append("\">");
            for (int offset = 0; offset < file.getValue().length; offset += BLOCK_SIZE) {
                var size = Math.min(BLOCK_SIZE, file.getValue().length - offset);
                digest.reset();
                digest.update(file.getValue(), offset, size);
                result.append("<Block Hash=\"")
                        .append(Base64.getEncoder().encodeToString(digest.digest())).append("\"/>");
            }
            result.append("</File>");
        }
        return result.append("</BlockMap>").toString();
    }

    private String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="png" ContentType="image/png" />
                  <Default Extension="db" ContentType="application/octet-stream" />
                  <Default Extension="xml" ContentType="application/vnd.ms-appx.manifest+xml" />
                  <Override PartName="/AppxBlockMap.xml" ContentType="application/vnd.ms-appx.blockmap+xml" />
                </Types>
                """;
    }

    private void putStored(ZipOutputStream zip, String name, byte[] content) throws java.io.IOException {
        var crc = new CRC32();
        crc.update(content);
        var entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
