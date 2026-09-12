package de.tum.cit.aet.artemis.core.config.migration;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Shared helpers for tests that inspect Liquibase changelog XML without touching a database.
 */
final class MigrationChangelogTestSupport {

    private MigrationChangelogTestSupport() {
    }

    /**
     * Creates a namespace-unaware, XXE-hardened {@link DocumentBuilder} for parsing changelog XML.
     * <p>
     * Namespace awareness is intentionally left disabled so {@code getElementsByTagName} resolves the Liquibase
     * elements by their plain local name despite the default changelog namespace.
     *
     * @return a hardened document builder
     * @throws Exception if the parser cannot be configured securely
     */
    static DocumentBuilder secureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Harden against XXE: no DOCTYPE, no external entities, no external DTD loading.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }
}
