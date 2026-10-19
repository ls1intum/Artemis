package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The indexes of every table, read from the database the server runs on. The query report uses
 * them to tell whether a repeated query can look its rows up through an index. They come from the
 * database rather than from the changelogs so that they match what the queries actually ran
 * against, whichever database that is.
 * <p>
 * Only indexes that any lookup on their columns can use are listed: a partial index (one with a
 * filter condition) covers only some rows, and an expression index such as {@code lower(email)}
 * serves only queries on that expression, so both are left out.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
public class IndexCatalog {

    private static final Logger log = LoggerFactory.getLogger(IndexCatalog.class);

    private static final Pattern PLAIN_COLUMN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataSource dataSource;

    /** Read on first use: the schema does not change after the migrations ran at startup. */
    private volatile Map<String, List<TableIndex>> indexes;

    public IndexCatalog(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns the indexes of every table.
     *
     * @return table name (lower case) to its indexes; empty if the database could not be read.
     */
    public Map<String, List<TableIndex>> indexes() {
        Map<String, List<TableIndex>> result = indexes;
        if (result == null) {
            synchronized (this) {
                result = indexes;
                if (result == null) {
                    result = read();
                    indexes = result;
                }
            }
        }
        return result;
    }

    private Map<String, List<TableIndex>> read() {
        // metadata calls do not run through the statement proxy, so they are not counted as queries of the report request
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            Map<String, List<TableIndex>> result = new TreeMap<>();
            for (String table : tableNames(metaData, catalog, schema)) {
                List<TableIndex> tableIndexes = tableIndexes(metaData, catalog, schema, table);
                if (!tableIndexes.isEmpty()) {
                    result.put(table.toLowerCase(Locale.ROOT), tableIndexes);
                }
            }
            log.info("[SlowQuery] Read {} indexes of {} tables", result.values().stream().mapToInt(List::size).sum(), result.size());
            return result;
        }
        catch (SQLException e) {
            log.warn("[SlowQuery] Could not read the database indexes; the report will not say which queries are indexed", e);
            return Map.of();
        }
    }

    private static List<String> tableNames(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet resultSet = metaData.getTables(catalog, schema, "%", new String[] { "TABLE" })) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private static List<TableIndex> tableIndexes(DatabaseMetaData metaData, String catalog, String schema, String table) throws SQLException {
        Map<String, IndexColumns> byName = new LinkedHashMap<>();
        Set<String> unusable = new HashSet<>();
        try (ResultSet resultSet = metaData.getIndexInfo(catalog, schema, table, false, true)) {
            while (resultSet.next()) {
                String name = resultSet.getString("INDEX_NAME");
                if (name == null || resultSet.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String column = unquote(resultSet.getString("COLUMN_NAME"));
                String filter = resultSet.getString("FILTER_CONDITION");
                if (column == null || !PLAIN_COLUMN.matcher(column).matches() || (filter != null && !filter.isBlank())) {
                    unusable.add(name);
                    continue;
                }
                boolean unique = !resultSet.getBoolean("NON_UNIQUE");
                byName.computeIfAbsent(name, k -> new IndexColumns(unique)).add(resultSet.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
            }
        }
        List<TableIndex> result = new ArrayList<>();
        byName.forEach((name, columns) -> {
            if (!unusable.contains(name)) {
                result.add(new TableIndex(name.toLowerCase(Locale.ROOT), columns.inOrder(), columns.unique));
            }
        });
        return result;
    }

    /** PostgreSQL quotes a column whose name is a reserved word, e.g. {@code "key"}. */
    private static String unquote(String column) {
        if (column != null && column.length() > 2 && column.startsWith("\"") && column.endsWith("\"")) {
            return column.substring(1, column.length() - 1);
        }
        return column;
    }

    /** The columns of one index as the metadata lists them, one row per column. */
    private static final class IndexColumns {

        private final boolean unique;

        private final Map<Short, String> byPosition = new TreeMap<>();

        private IndexColumns(boolean unique) {
            this.unique = unique;
        }

        private void add(short position, String column) {
            byPosition.put(position, column);
        }

        private List<String> inOrder() {
            return List.copyOf(byPosition.values());
        }
    }
}
