package simple.repo.rpm.model;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import simple.repo.model.FileIntegrityWithContent;
import simple.repo.model.FileSpecWithContent;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.ByteBufferBackedInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class RpmFile {
    Lead lead;
    Signature.Intro signatureIntro;
    Signature.Index signatureIndex;
    Signature signature;
    Signature.Intro headerIntro;
    Signature.Index headerIndex;
    Signature header;
    List<FileSpecWithContent> contents;

    @SneakyThrows
    public static RpmFile parse(ByteBuffer byteBuffer) {
        var lead = Lead.parse(byteBuffer);
        var intro = Signature.Intro.parse(byteBuffer);
        var index = Signature.Index.parse(intro, byteBuffer);
        var signature = Signature.parseSignature(index, byteBuffer);

        var headerIntro = Signature.Intro.parse(byteBuffer);
        var headerIndex = Signature.Index.parse(headerIntro, byteBuffer);
        var header = Signature.parseHeader(headerIndex, byteBuffer);

        byte[] decompressed;
        try (var compressorInputStream = new CompressorStreamFactory().createCompressorInputStream(new BufferedInputStream(new ByteBufferBackedInputStream(byteBuffer)))) {
            decompressed = compressorInputStream.readAllBytes();
        }

        List<FileSpecWithContent> contents = new ArrayList<>();
        try (var cpioArchiveInputStream = new CpioArchiveInputStream(new ByteArrayInputStream(decompressed))) {
            CpioArchiveEntry nextEntry;
            while (null != (nextEntry = cpioArchiveInputStream.getNextEntry())) {
                var content = cpioArchiveInputStream.readAllBytes();

                String path = nextEntry.getName();
                Integer mode = Math.toIntExact(nextEntry.getMode());
                FileIntegrityWithContent c = FileIntegrityWithContent.of(content, path);
                contents.add(new FileSpecWithContent(path, mode, c));
            }
        }

        return new RpmFile()
                .setLead(lead)
                .setSignatureIntro(intro)
                .setSignatureIndex(index)
                .setSignature(signature)
                .setHeaderIntro(headerIntro)
                .setHeaderIndex(headerIndex)
                .setHeader(header)
                .setContents(contents);
    }

    public int size() {
        return sizeWithoutContent() +
                contentsLength(contents);
    }

    public int sizeWithoutContent() {
        var preHeader = Lead.SIZE +
                Signature.Intro.SIZE + signatureIntro.indexSize() + signatureIntro.getDataLength();
        preHeader += (8 - (preHeader % 8)) % 8;
        return preHeader +
                Signature.Intro.SIZE + headerIntro.indexSize() + headerIntro.getDataLength();
    }

    @SneakyThrows
    private int contentsLength(List<FileSpecWithContent> contents) {
        var out = new ByteArrayOutputStream();

        try (var zstdOut = new ZstdCompressorOutputStream(out);
             var tarOut = new TarArchiveOutputStream(zstdOut)) {

            for (var content : contents) {
                var archiveEntry = new TarArchiveEntry(content.path());
                archiveEntry.setSize(content.content().getContent().length);
                archiveEntry.setMode(content.mode());
                tarOut.putArchiveEntry(archiveEntry);
                tarOut.write(content.content().getContent());
                tarOut.closeArchiveEntry();
            }
        }

        return out.toByteArray().length;
    }

    public byte[] toByteArray() {
        return toByteBuffer().array();
    }

    public ByteBuffer toByteBuffer() {
        return toByteBuffer(ByteBuffer.allocate(size()));
    }

    @SuppressWarnings("DuplicatedCode")
    public ByteBuffer toByteBuffer(ByteBuffer byteBuffer) {
        var offset = Lead.OFFSET;

        lead.toByteBuffer(byteBuffer.slice(offset, Lead.SIZE));
        offset += Lead.SIZE;

        signatureIntro.toByteBuffer(byteBuffer.slice(offset, Signature.Intro.SIZE));
        offset += Signature.Intro.SIZE;

        signatureIndex.toByteBuffer(byteBuffer.slice(offset, signatureIntro.indexSize()));
        offset += signatureIntro.indexSize();

        signature.toByteBuffer(byteBuffer.slice(offset, signatureIntro.getDataLength() + 8));
        offset += signatureIntro.getDataLength();

        offset += (offset % 8);

        headerIntro.toByteBuffer(byteBuffer.slice(offset, Signature.Intro.SIZE));
        offset += Signature.Intro.SIZE;

        headerIndex.toByteBuffer(byteBuffer.slice(offset, headerIntro.indexSize()));
        offset += headerIntro.indexSize();

        header.toByteBuffer(byteBuffer.slice(offset, headerIntro.getDataLength()));
        return byteBuffer;
    }

    @SneakyThrows
    static void main(String[] args) {
        Files.writeString(Path.of(System.getProperty("user.home"), "test.json"), JsonMapper.builder().build().writeValueAsString(RpmFile.parse(ByteBuffer.wrap(Files.readAllBytes(Path.of(args[0]))))));
    }
}
