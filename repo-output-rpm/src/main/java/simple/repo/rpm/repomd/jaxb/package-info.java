@XmlSchema(
        namespace = RpmXmlConstants.COMMON_NS,
        elementFormDefault = XmlNsForm.QUALIFIED,
        attributeFormDefault = XmlNsForm.UNQUALIFIED,
        xmlns = {
                @XmlNs(prefix = "", namespaceURI = RpmXmlConstants.COMMON_NS),
                @XmlNs(prefix = "rpm", namespaceURI = RpmXmlConstants.RPM_NS)
        }
)
package simple.repo.rpm.repomd.jaxb;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
import simple.repo.rpm.repomd.RpmXmlConstants;
