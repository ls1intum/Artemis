package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
 * {@code <sql>} blocks. {@code LONGTEXT} is matched as a whole word, so decorated declarations such as
 * {@code LONGTEXT CHARACTER SET utf8mb4} are still detected. XML comments are ignored by the parser, so mentioning the
 * word in a comment does not trigger it.
 */
class LiquibaseChangelogDataTypeTest {

    private static final String CHANGELOG_LOCATION_PATTERN = "classpath*:config/liquibase/changelog/*.xml";

    /**
     * The initial schema predates the bounded-type policy and legitimately contains {@code longtext} columns.
     */
    private static final String GRANDFATHERED_BASELINE = "00000000000000_initial_schema.xml";

    private static final Pattern LONGTEXT_TOKEN = Pattern.compile("\\bLONGTEXT\\b", Pattern.CASE_INSENSITIVE);

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

    @Test
    void isLongtextDataTypeDetectsPlainAndDecoratedDeclarations() {
        assertThat(isLongtextDataType("longtext")).isTrue();
        assertThat(isLongtextDataType("LONGTEXT")).isTrue();
        assertThat(isLongtextDataType("LONGTEXT CHARACTER SET utf8mb4")).isTrue();
        assertThat(isLongtextDataType("mediumtext")).isFalse();
        assertThat(isLongtextDataType("text")).isFalse();
        assertThat(isLongtextDataType("varchar(255)")).isFalse();
        assertThat(isLongtextDataType(null)).isFalse();
        assertThat(isLongtextDataType("verylongtextfield")).isFalse();
    }

    private void collectLongtextViolations(Resource changelog, String fileName, List<String> violations) throws Exception {
        Document document;
        try (InputStream inputStream = changelog.getInputStream()) {
            document = MigrationChangelogTestSupport.secureDocumentBuilder().parse(inputStream);
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
            if (sqlText != null && LONGTEXT_TOKEN.matcher(sqlText).find()) {
                violations.add(fileName + " (inline <sql> block references LONGTEXT)");
            }
        }
    }

    private void checkDataTypeAttribute(Node attribute, String fileName, List<String> violations) {
        if (attribute != null && isLongtextDataType(attribute.getNodeValue())) {
            violations.add(fileName + " (" + attribute.getNodeName() + "=\"" + attribute.getNodeValue() + "\")");
        }
    }

    /**
     * Reports whether a Liquibase datatype value declares a {@code LONGTEXT} column. {@code LONGTEXT} is matched as a
     * whole word, so a decorated value such as {@code LONGTEXT CHARACTER SET utf8mb4} still counts while an unrelated
     * token such as {@code verylongtextfield} does not.
     *
     * @param dataType the raw {@code type} / {@code newDataType} attribute value, may be {@code null}
     * @return {@code true} if the value declares a {@code LONGTEXT} column
     */
    private static boolean isLongtextDataType(String dataType) {
        return dataType != null && LONGTEXT_TOKEN.matcher(dataType).find();
    }
}
