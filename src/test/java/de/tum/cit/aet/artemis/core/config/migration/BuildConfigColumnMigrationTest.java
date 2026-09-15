package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Verifies the structure of the MySQL migration in {@code 20260819120000_changelog.xml} without touching a database:
 * oversized {@code build_plan_configuration} / {@code docker_flags} values are reset to NULL before both columns are
 * narrowed from LONGTEXT to MEDIUMTEXT, and the change is gated to MySQL only.
 * <p>
 * The changeset is MySQL only (dbms precondition, MARK_RAN elsewhere) and therefore never runs in the PostgreSQL CI
 * suite; the application-level size guard added separately prevents new oversized values. This test asserts the
 * changelog keeps its safety shape (NULL reset before the type change), so a regression in the migration is caught.
 */
class BuildConfigColumnMigrationTest {

    private static final String CHANGELOG = "config/liquibase/changelog/20260819120000_changelog.xml";

    private static final String CHANGE_SET_ID = "20260819120000-1";

    private static final String TABLE = "programming_exercise_build_config";

    private static final String BUILD_PLAN_COLUMN = "build_plan_configuration";

    private static final String DOCKER_FLAGS_COLUMN = "docker_flags";

    // MEDIUMTEXT holds at most 16_777_215 bytes; values above this are reset to NULL before the column is narrowed.
    private static final int MEDIUMTEXT_MAX_BYTES = 16_777_215;

    @Test
    void migrationIsGatedToMySqlOnly() throws Exception {
        Element changeSet = loadChangeSet();

        Element preConditions = singleDirectChild(changeSet, "preConditions");
        assertThat(preConditions.getAttribute("onFail")).isEqualTo("MARK_RAN");

        // The dbms guard must live inside that same preConditions block, not merely somewhere in the changeSet. It is
        // looked up as a descendant because Liquibase may nest preconditions inside <and> / <or> wrappers.
        Element dbms = singleDescendant(preConditions, "dbms");
        assertThat(dbms.getAttribute("type")).isEqualTo("mysql");
    }

    @Test
    void migrationNarrowsBothColumnsToMediumText() throws Exception {
        Element changeSet = loadChangeSet();

        List<Element> modifications = directChildElements(changeSet, "modifyDataType");
        assertThat(modifications).as("both columns must be narrowed").hasSize(2);

        List<String> columns = new ArrayList<>();
        for (Element modification : modifications) {
            assertThat(modification.getAttribute("tableName")).isEqualTo(TABLE);
            assertThat(modification.getAttribute("newDataType")).isEqualToIgnoringCase("MEDIUMTEXT");
            columns.add(modification.getAttribute("columnName"));
        }
        assertThat(columns).containsExactlyInAnyOrder(BUILD_PLAN_COLUMN, DOCKER_FLAGS_COLUMN);
    }

    @Test
    void migrationResetsOversizedValuesToNullBeforeNarrowing() throws Exception {
        Element changeSet = loadChangeSet();

        List<String> statements = new ArrayList<>();
        for (Element sql : directChildElements(changeSet, "sql")) {
            statements.add(normalize(sql.getTextContent()));
        }
        assertThat(statements).as("one NULL-reset statement per column").hasSize(2);

        assertThat(statements).anySatisfy(statement -> assertResetsColumn(statement, BUILD_PLAN_COLUMN));
        assertThat(statements).anySatisfy(statement -> assertResetsColumn(statement, DOCKER_FLAGS_COLUMN));

        // The reset must run before the narrowing, otherwise modifyDataType could fail or truncate an oversized value.
        List<String> childOrder = directChildElementNames(changeSet);
        int lastReset = childOrder.lastIndexOf("sql");
        int firstNarrowing = childOrder.indexOf("modifyDataType");
        assertThat(lastReset).as("NULL reset statements must be direct children of the changeSet").isNotNegative();
        assertThat(firstNarrowing).as("both columns must be narrowed").isNotNegative();
        assertThat(lastReset).as("every NULL reset must precede the first column narrowing").isLessThan(firstNarrowing);
    }

    private void assertResetsColumn(String statement, String column) {
        String columnToken = column.toUpperCase(Locale.ROOT);
        assertThat(statement).contains("UPDATE " + TABLE.toUpperCase(Locale.ROOT));
        assertThat(statement).contains("SET " + columnToken + " = NULL");
        assertThat(statement).contains("OCTET_LENGTH(" + columnToken + ") > " + MEDIUMTEXT_MAX_BYTES);
    }

    private Element loadChangeSet() throws Exception {
        Resource resource = new ClassPathResource(CHANGELOG);
        Document document;
        try (InputStream inputStream = resource.getInputStream()) {
            document = MigrationChangelogTestSupport.secureDocumentBuilder().parse(inputStream);
        }

        List<Element> changeSets = new ArrayList<>();
        NodeList candidates = document.getElementsByTagName("changeSet");
        for (int i = 0; i < candidates.getLength(); i++) {
            Element candidate = (Element) candidates.item(i);
            if (CHANGE_SET_ID.equals(candidate.getAttribute("id"))) {
                changeSets.add(candidate);
            }
        }
        assertThat(changeSets).as("exactly one changeSet with id %s", CHANGE_SET_ID).hasSize(1);

        Element changeSet = changeSets.get(0);
        assertThat(changeSet.getAttribute("author")).as("changeSet author must be set").isNotBlank();
        return changeSet;
    }

    private List<String> directChildElementNames(Element parent) {
        List<String> names = new ArrayList<>();
        for (Element child : directChildElements(parent)) {
            names.add(child.getNodeName());
        }
        return names;
    }

    private Element singleDirectChild(Element parent, String tagName) {
        List<Element> found = directChildElements(parent, tagName);
        assertThat(found).as("exactly one direct <%s> child expected", tagName).hasSize(1);
        return found.get(0);
    }

    /**
     * Returns the migration operations of the given type as DIRECT children only, so operations nested in a
     * {@code <rollback>} (or any other wrapper) cannot satisfy the forward-migration assertions.
     */
    private List<Element> directChildElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        for (Element child : directChildElements(parent)) {
            if (child.getNodeName().equals(tagName)) {
                result.add(child);
            }
        }
        return result;
    }

    private List<Element> directChildElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) child);
            }
        }
        return result;
    }

    private Element singleDescendant(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        assertThat(nodes.getLength()).as("exactly one <%s> descendant expected", tagName).isEqualTo(1);
        return (Element) nodes.item(0);
    }

    private String normalize(String sql) {
        return sql.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
