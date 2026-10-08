package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.Association;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ForeignKeyDescriptor;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Schema check: finds foreign-key columns that are not the leading column of any index.
 * <p>
 * An unindexed foreign key makes every join, lookup and cascading delete on that column scan the
 * whole table. That is invisible in E2E runs (their tables are tiny) and painful in production,
 * which is why this is a schema check and not a timing one.
 * <p>
 * The foreign keys come from Hibernate's own mapping model rather than from reading annotations:
 * Hibernate has already resolved inheritance (a single-table subclass's foreign keys live in its
 * root table, a joined subclass's inherited ones in its parent's), implicit column names, and the
 * collection side of one-to-many and many-to-many associations. The indexes come from the live,
 * fully-migrated schema of the Postgres this test suite boots anyway, which is the ground truth
 * regardless of how many Liquibase changeSets created, replaced or dropped them.
 * <p>
 * Informational: writes every finding, keyed {@code missing_fk_index:table.column}, to
 * {@code build/reports/static-analysis/missing-fk-indexes.json} and does not fail the build.
 */
class MissingForeignKeyIndexTest extends AbstractSpringIntegrationIndependentTest {

    private static final Path OUTPUT_PATH = Path.of("build", "reports", "static-analysis", "missing-fk-indexes.json");

    /**
     * A foreign-key column with no index that starts with it.
     *
     * @param key        stable key for comparing runs, {@code missing_fk_index:table.column}.
     * @param entity     entity declaring the association.
     * @param attribute  association attribute, for locating it in the source.
     * @param tableName  table holding the foreign-key column.
     * @param columnName the foreign-key column.
     */
    record MissingForeignKeyIndex(String key, String entity, String attribute, String tableName, String columnName) {
    }

    private record ForeignKeyColumn(String entity, String attribute, String tableName, String columnName) {
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @Test
    void writeMissingForeignKeyIndexReport() throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assumeTrue(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres"), "the index lookup reads the Postgres catalog");
        }

        List<ForeignKeyColumn> foreignKeys = findForeignKeyColumns();
        Map<String, Set<String>> leadingIndexColumns = findLeadingIndexColumns();
        List<MissingForeignKeyIndex> missing = foreignKeys.stream().filter(fk -> !leadingIndexColumns.getOrDefault(fk.tableName(), Set.of()).contains(fk.columnName()))
                .map(fk -> new MissingForeignKeyIndex("missing_fk_index:" + fk.tableName() + "." + fk.columnName(), fk.entity(), fk.attribute(), fk.tableName(), fk.columnName()))
                .toList();

        Files.createDirectories(OUTPUT_PATH.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(OUTPUT_PATH.toFile(), missing);

        // guards against the mapping walk silently finding nothing (e.g. after a Hibernate API change);
        // the findings themselves are reported, not gated
        assertThat(foreignKeys).as("the Hibernate mapping model should expose foreign keys").hasSizeGreaterThan(50);
    }

    /**
     * Every foreign-key column Hibernate maps, once per (table, column), sorted for a stable report.
     * For a to-one association only the side that owns the foreign key (KEY) is taken; a mappedBy
     * side has no column of its own. For a collection, its key column lives in the child table
     * (one-to-many) or the join table (many-to-many and element collections), and for a
     * many-to-many the join table's column pointing at the element is a foreign key too.
     */
    private List<ForeignKeyColumn> findForeignKeyColumns() {
        Map<String, ForeignKeyColumn> byColumn = new TreeMap<>();
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(persister -> {
            String entity = simpleName(persister.getEntityName());
            // associations that are part of a composite primary key (@IdClass / @EmbeddedId) are not
            // regular attributes; a key (user_id, course_id) does not serve lookups on course_id alone
            if (persister.getIdentifierMapping() instanceof EmbeddableValuedModelPart compositeId) {
                compositeId.getEmbeddableTypeDescriptor().forEachAttributeMapping(attribute -> collectForeignKeys(attribute, entity, byColumn));
            }
            persister.forEachAttributeMapping(attribute -> collectForeignKeys(attribute, entity, byColumn));
        });
        return byColumn.values().stream().sorted(Comparator.comparing(ForeignKeyColumn::tableName).thenComparing(ForeignKeyColumn::columnName)).toList();
    }

    private static void collectForeignKeys(AttributeMapping attribute, String entity, Map<String, ForeignKeyColumn> byColumn) {
        if (attribute instanceof Association association && association.getSideNature() == ForeignKeyDescriptor.Nature.KEY) {
            addKeyColumns(association.getForeignKeyDescriptor(), attribute, entity, byColumn);
        }
        if (attribute instanceof PluralAttributeMapping collection) {
            addKeyColumns(collection.getKeyDescriptor(), attribute, entity, byColumn);
            if (collection.getElementDescriptor() instanceof Association element && element.getSideNature() == ForeignKeyDescriptor.Nature.KEY) {
                addKeyColumns(element.getForeignKeyDescriptor(), attribute, entity, byColumn);
            }
        }
        // associations inside an @Embedded value
        if (attribute instanceof EmbeddableValuedModelPart embedded) {
            embedded.getEmbeddableTypeDescriptor().forEachAttributeMapping(nested -> collectForeignKeys(nested, entity, byColumn));
        }
    }

    private static void addKeyColumns(ForeignKeyDescriptor foreignKey, AttributeMapping attribute, String persisterEntity, Map<String, ForeignKeyColumn> byColumn) {
        if (foreignKey == null) {
            return;
        }
        // name the entity that declares the attribute, not every subclass that inherits it
        String entity = attribute.getDeclaringType() instanceof EntityMappingType declaring ? simpleName(declaring.getEntityName()) : persisterEntity;
        foreignKey.getKeyPart().forEachSelectable((index, selectable) -> {
            if (!selectable.isFormula()) {
                String table = unquote(selectable.getContainingTableExpression());
                String column = unquote(selectable.getSelectionExpression());
                // keyed by table and column, so an attribute inherited by many subclasses is one finding
                byColumn.putIfAbsent(table + "." + column, new ForeignKeyColumn(entity, attribute.getAttributeName(), table, column));
            }
        });
    }

    /** table name -> the columns that lead at least one valid index on it. */
    private Map<String, Set<String>> findLeadingIndexColumns() throws SQLException {
        // a composite index only serves lookups on its prefix, so only its first column counts
        String sql = """
                SELECT t.relname AS table_name, a.attname AS column_name
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ix.indkey[0]
                WHERE ix.indisvalid AND n.nspname = current_schema()
                """;
        Map<String, Set<String>> result = new TreeMap<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.computeIfAbsent(resultSet.getString("table_name"), table -> new HashSet<>()).add(resultSet.getString("column_name"));
            }
        }
        return result;
    }

    private static String unquote(String identifier) {
        return identifier.replace("\"", "").replace("`", "").toLowerCase(Locale.ROOT);
    }

    private static String simpleName(String entityName) {
        return entityName.substring(entityName.lastIndexOf('.') + 1);
    }
}
