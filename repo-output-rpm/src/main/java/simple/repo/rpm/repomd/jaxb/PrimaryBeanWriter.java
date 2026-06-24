package simple.repo.rpm.repomd.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import lombok.SneakyThrows;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class PrimaryBeanWriter {
    private final JAXBContext context;

    @SneakyThrows
    public PrimaryBeanWriter() {
        this.context = JAXBContext.newInstance(PrimaryBean.class);
    }

    @SneakyThrows
    public String writeToString(PrimaryBean dto) {
        Marshaller marshaller = context.createMarshaller();

        // produces double lines with the transformer indent below
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty("org.glassfish.jaxb.indentString", String.valueOf(' ').repeat(2));
        marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());

        StringWriter writer = new StringWriter();
        marshaller.marshal(dto, writer);
        return writer.toString();
    }
}
