package de.tum.cit.aet.artemis.core.config.performance;

import java.util.List;

/**
 * An index of a database table, as {@link IndexCatalog} reads it from the database itself.
 *
 * @param name    the index name, lower case.
 * @param columns the indexed columns in index order, lower case; a lookup can use the index only
 *                    through a prefix of them.
 * @param unique  whether the index enforces uniqueness (a primary key or unique constraint does).
 */
public record TableIndex(String name, List<String> columns, boolean unique) {
}
