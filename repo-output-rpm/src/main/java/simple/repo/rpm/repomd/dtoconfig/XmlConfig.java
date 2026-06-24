package simple.repo.rpm.repomd.dtoconfig;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.codehaus.stax2.XMLStreamWriter2;
import simple.repo.rpm.repomd.RpmXmlConstants;


public interface XmlConfig {
    class DelegatingXmlStreamWriter implements XMLStreamWriter2 {
        @Getter
        @Setter
        @Delegate
        XMLStreamWriter2 delegate;

    }

    class PrefixingXmlStreamWriter extends DelegatingXmlStreamWriter {
        public PrefixingXmlStreamWriter(XMLStreamWriter2 delegate) {
            setDelegate(delegate);
        }

        @SneakyThrows
        public void setDelegate(XMLStreamWriter2 delegate) {
            this.delegate = delegate;
            this.delegate.setPrefix("rpm", RpmXmlConstants.RPM_NS);
            this.delegate.setDefaultNamespace(RpmXmlConstants.COMMON_NS);
        }

        @SneakyThrows
        @Override
        public void writeStartDocument() {
            delegate.writeStartDocument();
            delegate.writeNamespace("", RpmXmlConstants.COMMON_NS);
            delegate.writeNamespace("rpm", RpmXmlConstants.RPM_NS);
        }

        @SneakyThrows
        @Override
        public void writeStartElement(String namespaceURI, String localName) {
            String prefix = delegate.getPrefix(namespaceURI);
            delegate.writeStartElement(prefix, localName, namespaceURI);
        }
    }
}
