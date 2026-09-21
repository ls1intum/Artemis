package de.tum.cit.aet.artemis.core.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the association whose row owns this entity's lifetime.
 * <p>
 * A parent is not the same thing as a reference. A result references its assessor and outlives them; a result belongs
 * to its submission and has no meaning without it. Only the second is a parent, and only a parent has to be there,
 * which is why the distinction cannot be left to the reader of a nullable {@code @ManyToOne}.
 * <p>
 * A parent must be required. For a single parent that means {@code optional = false} on the association or
 * {@code nullable = false} on its {@code @JoinColumn}; the database says the same thing with {@code NOT NULL}. Where an
 * entity may belong to one of several parents, each of them carries {@link #enforcedBy()} naming the check constraint
 * that makes exactly one of them present, because no single column can express that on its own.
 * <p>
 * {@code EntityOwnershipArchitectureTest} reads the mapping and holds the list of entities that do not satisfy this
 * yet; {@code EntityOwnershipDatabaseTest} reads the migrated schema, so that a declaration cannot claim a guarantee
 * only the mapping makes.
 *
 * @see AggregateRoot
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Parent {

    /**
     * The database check constraint that makes exactly one of several alternative parents present.
     * <p>
     * Empty when this parent is required on its own. When set, a check constraint of that name has to exist on the
     * entity's table, so that an entity cannot claim a guarantee the schema does not make.
     *
     * @return the constraint name, or an empty string for a parent that is required by itself
     */
    String enforcedBy() default "";
}
