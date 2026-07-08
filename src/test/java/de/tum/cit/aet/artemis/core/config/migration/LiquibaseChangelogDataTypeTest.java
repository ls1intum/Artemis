package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Guards against future Liquibase migrations introducing columns of type {@code LONGTEXT}.
 * <p>
 * {@code LONGTEXT} (up to 4 GB on MySQL) is larger than needed for the values stored here; new columns should use a
 * bounded type (e.g. {@code MEDIUMTEXT}, {@code TEXT} or {@code VARCHAR}). The initial baseline schema is grandfathered;
 * every incremental changelog must comply.
 * <p>
 * The check inspects the {@code type} / {@code newDataType} attributes of all changelog elements and the text of inline
 * {@code <sql>} blocks. XML comments are ignored by the parser, so mentioning the word in a comment does not trigger it.
 */
class LiquibaseChangelogDataTypeTest {

    private static final String CHANGELOG_LOCATION_PATTERN = "classpath*:config/liquibase/changelog/*.xml";

    /**
     * The initial schema predates the bounded-type policy and legitimately contains {@code longtext} columns.
     */
    private static final String GRANDFATHERED_BASELINE = "00000000000000_initial_schema.xml";

    private static final Pattern LONGTEXT_IN_SQL = Pattern.compile("\\blongtext\\b", Pattern.CASE_INSENSITIVE);

    @Test
    void testNoNewLongtextDataTypeInIncrementalChangelogs() throws Exception {
        Resource[] changelogs = new PathMatchingResourcePatternResolver().getResources(CHANGELOG_LOCATION_PATTERN);
        assertThat(changelogs).as("Liquibase changelogs must be discoverable on the classpath").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Resource changelog : changelogs) {
            String fileName = changelog.getFilename();
            if (fileName == null || GRANDFATHERED_BASELINE.equals(fileName)) {
                continue;
            }
            collectLongtextViolations(changelog, fileName, violations);
        }

        assertThat(violations).as("New Liquibase migrations must not introduce LONGTEXT columns. Use a bounded type such as MEDIUMTEXT, TEXT or VARCHAR instead. " + "The baseline "
                + GRANDFATHERED_BASELINE + " is grandfathered. Offending occurrences: " + violations).isEmpty();
    }

    private void collectLongtextViolations(Resource changelog, String fileName, List<String> violations) throws Exception {
        Document document;
        try (InputStream inputStream = changelog.getInputStream()) {
            document = createSecureDocumentBuilder().parse(inputStream);
        }

        // Check the datatype attributes of all elements (e.g. <column type="..."/>, <modifyDataType newDataType="..."/>).
        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            NamedNodeMap attributes = allElements.item(i).getAttributes();
            if (attributes == null) {
                continue;
            }
            checkDataTypeAttribute(attributes.getNamedItem("type"), fileName, violations);
            checkDataTypeAttribute(attributes.getNamedItem("newDataType"), fileName, violations);
        }

        // Check the text of inline <sql> blocks (raw SQL migrations).
        NodeList sqlBlocks = document.getElementsByTagName("sql");
        for (int i = 0; i < sqlBlocks.getLength(); i++) {
            String sqlText = sqlBlocks.item(i).getTextContent();
            if (sqlText != null && LONGTEXT_IN_SQL.matcher(sqlText).find()) {
                violations.add(fileName + " (inline <sql> block references LONGTEXT)");
            }
        }
    }

    private void checkDataTypeAttribute(Node attribute, String fileName, List<String> violations) {
        if (attribute != null && "longtext".equals(attribute.getNodeValue().trim().toLowerCase(Locale.ROOT))) {
            violations.add(fileName + " (" + attribute.getNodeName() + "=\"" + attribute.getNodeValue() + "\")");
        }
    }

    private DocumentBuilder createSecureDocumentBuilder() throws Exception {
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
