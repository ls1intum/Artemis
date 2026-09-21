package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Holds every declared {@code @Parent} against the database the migrations actually produced.
 * <p>
 * {@link EntityOwnershipArchitectureTest} reads the mapping: it says which entities declare a parent and which of those
 * declarations claim to be required. A mapping cannot establish what the database does, though - {@code nullable =
 * false} on a {@code @JoinColumn} is a statement about the entity, and Hibernate does not verify it against the schema -
 * so on its own that gate would pass for a parent no migration ever constrained. This test closes that gap from the
 * other end: it reads {@code information_schema} after Liquibase has run and asserts the column really is
 * {@code NOT NULL}, and that a named alternative-parent constraint really exists on the entity's table.
 * <p>
 * Reading the migrated schema rather than the changelog is deliberate. The changelog is a history: a column can be
 * created nullable, made {@code NOT NULL}, widened by a {@code modifyDataType} that silently drops it, and constrained
 * again, and only the end state is the guarantee. Text in a changelog also proves nothing about which statements ran,
 * since a name occurring in a comment or inside a {@code <rollback>} reads exactly like one in a change.
 *
 * @see Parent
 * @see EntityOwnershipArchitectureTest
 */
class EntityOwnershipDatabaseTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * One declared parent, resolved to the table and column Hibernate maps it to.
     *
     * @param field             where the declaration is, for the failure message
     * @param table             the table holding the column
     * @param column            the column holding the parent's key
     * @param requiredInMapping whether the mapping refuses a row without this parent
     * @param enforcedBy        the check constraint the declaration names, or an empty string
     */
    private record DeclaredParent(String field, String table, String column, boolean requiredInMapping, String enforcedBy) {
    }

    @Test
    void everyParentTheMappingRequiresIsNotNullInTheDatabase() {
        List<String> notEnforced = new ArrayList<>();
        for (DeclaredParent parent : declaredParents()) {
            // A parent with alternatives is the one case where the column is allowed to be nullable on its own: what
            // makes it required is the check constraint over all of them, which the next test reads. The parents that
            // are not required yet are the backlog EntityOwnershipArchitectureTest holds.
            if (!parent.requiredInMapping() || !parent.enforcedBy().isEmpty()) {
                continue;
            }
            List<String> nullability = jdbcTemplate.queryForList("SELECT is_nullable FROM information_schema.columns WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?",
                    String.class, parent.table(), parent.column());
            if (nullability.isEmpty()) {
                notEnforced.add(parent.field() + " -> " + parent.table() + "." + parent.column() + " (no such column)");
            }
            else if (nullability.stream().anyMatch(value -> !"NO".equalsIgnoreCase(value))) {
                notEnforced.add(parent.field() + " -> " + parent.table() + "." + parent.column() + " (nullable)");
            }
        }

        assertThat(notEnforced).as("""
                The mapping refuses a row without these parents, and the database does not. A mapping-only guarantee \
                holds for what Hibernate writes and for nothing else, so any other writer - a migration, a native \
                query, a second application - can still produce a row that belongs to nothing. Add the Liquibase \
                change that makes the column NOT NULL.""").isEmpty();
    }

    @Test
    void everyAlternativeParentConstraintExistsInTheDatabase() {
        List<String> missing = new ArrayList<>();
        for (DeclaredParent parent : declaredParents()) {
            if (parent.enforcedBy().isEmpty()) {
                continue;
            }
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE LOWER(table_name) = ? AND UPPER(constraint_name) = ? AND constraint_type = 'CHECK'",
                    Integer.class, parent.table(), parent.enforcedBy().toUpperCase(Locale.ROOT));
            if (found == null || found == 0) {
                missing.add(parent.field() + " -> " + parent.enforcedBy() + " on " + parent.table());
            }
        }

        assertThat(missing).as("""
                @Parent(enforcedBy = ...) claims the database makes exactly one of several parents present. The named \
                check constraint is not on the entity's table, so nothing stops a row that names none of them. Add the \
                constraint in a Liquibase changelog, or drop the claim.""").isEmpty();
    }

    /**
     * Every {@code @Parent} declaration in the mapping, with the table and column Hibernate resolved it to.
     * <p>
     * Asking Hibernate rather than rebuilding the naming rules means the column named here is the one the entity
     * actually reads and writes, including where the mapping leaves the name to the naming strategy, and that
     * "required" means what Hibernate concluded from {@code optional} and {@code nullable} together rather than from
     * whichever of the two a reader looked at. A declaration inherited by several subclasses resolves to the same
     * table and column for each of them, so the set collapses them back into one.
     *
     * @return the declared parents
     */
    private Set<DeclaredParent> declaredParents() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        Set<DeclaredParent> parents = new LinkedHashSet<>();
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(descriptor -> {
            if (!(descriptor instanceof AbstractEntityPersister persister)) {
                return;
            }
            for (Class<?> type = persister.getMappedClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!field.isAnnotationPresent(Parent.class)) {
                        continue;
                    }
                    String[] columns = persister.getPropertyColumnNames(field.getName());
                    // A parent mapped onto several columns is a composite key, which cannot be half present, and one
                    // mapped onto none is shared with the entity's own primary key through @MapsId.
                    if (columns == null || columns.length != 1) {
                        continue;
                    }
                    // The table of the entity that declares the field, not of the subclass being walked: under joined
                    // inheritance the subclass has a table of its own, and the column lives on the base table.
                    String table = tableOf(sessionFactory, type, persister);
                    parents.add(new DeclaredParent(type.getSimpleName() + "." + field.getName(), table.toLowerCase(Locale.ROOT), columns[0].toLowerCase(Locale.ROOT),
                            isRequired(persister, field.getName()), field.getAnnotation(Parent.class).enforcedBy()));
                }
            }
        });
        return parents;
    }

    private static String tableOf(SessionFactoryImplementor sessionFactory, Class<?> declaringType, AbstractEntityPersister fallback) {
        EntityPersister declaring = sessionFactory.getMappingMetamodel().findEntityDescriptor(declaringType);
        return declaring instanceof AbstractEntityPersister declaringPersister ? declaringPersister.getTableName() : fallback.getTableName();
    }

    private static boolean isRequired(AbstractEntityPersister persister, String propertyName) {
        int index = Arrays.asList(persister.getPropertyNames()).indexOf(propertyName);
        return index >= 0 && !persister.getPropertyNullability()[index];
    }
}
