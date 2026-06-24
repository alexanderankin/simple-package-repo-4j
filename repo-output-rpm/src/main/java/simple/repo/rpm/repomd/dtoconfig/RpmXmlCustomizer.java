package simple.repo.rpm.repomd.dtoconfig;

import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.stax.WstxOutputFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.jackson.autoconfigure.XmlMapperBuilderCustomizer;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper.Builder;
import tools.jackson.dataformat.xml.XmlWriteFeature;

public class RpmXmlCustomizer implements XmlMapperBuilderCustomizer {
    // static boolean tryToUsePrefixMap = false;
    // static XMLOutputFactory xmlOut = new Ns();

    // public static Builder thing() {
    //     return XmlMapper.builder(XmlFactory.builder()
    //             .xmlOutputFactory(xmlOut)
    //             .build());
    // }

    public Builder customized(Builder builder) {
        customize(builder);
        return builder;
    }

    @Override
    public void customize(@NonNull Builder builder) {
        /*
        if (tryToUsePrefixMap) {
            if (!(builder.streamFactory() instanceof XmlFactory x)) {
                throw new IllegalStateException();
            }


            XMLOutputFactory xf = x.getXMLOutputFactory();
            if (!(xf instanceof WstxOutputFactory w))
                throw new UnsupportedOperationException();

            if (!(w instanceof Ns ns))
                throw new IllegalArgumentException("need XMLOutputFactory with good namespace support");

            // w.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
            // w.setProperty(WstxOutputProperties.P_OUTPUT_UNDERLYING_STREAM, false);

            ns.getNsMap().putAll(Map.of(
                    URI.create(RpmXmlConstants.REPO_NS), "repo",
                    URI.create(RpmXmlConstants.RPM_NS), "rpm"
            ));
        }
        */

        if (builder.streamFactory() instanceof XmlFactory x &&
                x.getXMLOutputFactory() instanceof WstxOutputFactory w) {
            w.setProperty(WstxOutputProperties.P_USE_DOUBLE_QUOTES_IN_XML_DECL, true);
        }

        builder
                .configure(XmlWriteFeature.WRITE_XML_DECLARATION, true)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }
    /*

    @EqualsAndHashCode(callSuper = false)
    @ToString(onlyExplicitlyIncluded = true)
    @Getter
    @Setter
    @Accessors(chain = true)
    private static class Ns extends WstxOutputFactory {
        @ToString.Include
        Map<URI, String> nsMap = new HashMap<>();

        @Override
        public XMLStreamWriter createXMLStreamWriter(Writer w) throws XMLStreamException {
            XMLStreamWriter delegate = super.createXMLStreamWriter(w);

            for (var entry : nsMap.entrySet()) {
                delegate.setPrefix(entry.getValue(), entry.getKey().toString());

            }
            return new NamespaceWriter(delegate).setNsMap(nsMap);
        }

        @RequiredArgsConstructor
        @Getter
        @Setter
        @Accessors(chain = true)
        static class DelegatingWriter implements XMLStreamWriter {
            @Delegate
            protected final XMLStreamWriter d;
        }

        @Getter
        @Setter
        @Accessors(chain = true)
        static class NamespaceWriter extends DelegatingWriter {
            Map<URI, String> nsMap = new HashMap<>();

            public NamespaceWriter(XMLStreamWriter d) {
                super(d);
            }


            protected String prefix(String namespaceURI, String fallback) {
                if (namespaceURI == null || namespaceURI.isBlank()) {
                    return fallback;
                }

                return nsMap.getOrDefault(
                        URI.create(namespaceURI),
                        fallback
                );
            }

            @Override
            public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
                var prefix = prefix(namespaceURI, "");
                d.writeStartElement(prefix, localName, namespaceURI);
            }

            @Override
            public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
                d.writeStartElement(prefix(namespaceURI, prefix), localName, namespaceURI);
            }

            @Override
            public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
                d.writeEmptyElement(prefix(namespaceURI, ""), localName, namespaceURI);
            }

            @Override
            public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
                d.writeEmptyElement(prefix(namespaceURI, prefix), localName, namespaceURI);
            }

            @Override
            public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
                d.writeAttribute(prefix(namespaceURI, ""), namespaceURI, localName, value);
            }

            @Override
            public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
                d.writeAttribute(prefix(namespaceURI, prefix), namespaceURI, localName, value);
            }


            @Override
            public void setPrefix(String prefix, String uri) throws XMLStreamException {
                nsMap.put(URI.create(uri), prefix);
                d.setPrefix(prefix, uri);
            }
        }
    }
    */
}
