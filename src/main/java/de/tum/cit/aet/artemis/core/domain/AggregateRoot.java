package de.tum.cit.aet.artemis.core.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that this entity belongs to nothing: it is the root of its own aggregate and has no parent row.
 * <p>
 * Every entity is either a root or belongs to another one through a {@link Parent} association. Which of the two it is
 * cannot be read off the mapping, because a nullable {@code @ManyToOne} looks the same whether the association is a
 * parent nobody made required or a reference that genuinely may be absent. This annotation says which, so that
 * {@code EntityOwnershipArchitectureTest} can hold every entity to one rule or the other.
 * <p>
 * The reason matters more than the annotation. A root that is one only because nobody has required its parent yet is a
 * bug being declared rather than fixed, so write down what makes the entity independent: it is reference data, an
 * audit record, a server-wide setting, or an aggregate the rest of the model hangs off.
 *
 * @see Parent
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AggregateRoot {

    /**
     * Why this entity belongs to nothing.
     *
     * @return the reason, written for whoever is deciding whether a new entity may copy this
     */
    String value();
}
