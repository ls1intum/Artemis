package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;

import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link SecureXmlFactory} actually closes the external-entity paths.
 * <p>
 * Asserted on the factory's own configuration rather than by transforming a hostile document, because a factory that
 * is missing one of these properties still transforms every well-formed document without complaining — the difference
 * only shows for a document that references something external, and a test that has to reach a file or a network
 * address to prove that is the wrong shape of test. These are the three properties the finding is about.
 */
class SecureXmlFactoryTest {

    @Test
    void testTransformerFactoryForbidsExternalDtdAndStylesheet() {
        TransformerFactory factory = SecureXmlFactory.transformerFactory();

        assertThat(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)).as("secure processing must be enabled").isTrue();
        assertThat(factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_DTD)).as("no protocol may be allowed for an external DTD").isEqualTo("");
        assertThat(factory.getAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET)).as("no protocol may be allowed for an external stylesheet").isEqualTo("");
    }

    @Test
    void testTransformerCanStillTransformAnOrdinaryDocument() throws Exception {
        // The hardening must not break the export it protects.
        var transformer = SecureXmlFactory.transformer();
        var writer = new java.io.StringWriter();
        var source = new javax.xml.transform.stream.StreamSource(java.io.Reader.of("<project><name>Artemis</name></project>"));

        transformer.transform(source, new javax.xml.transform.stream.StreamResult(writer));

        assertThat(writer.toString()).contains("<name>Artemis</name>");
    }
}
