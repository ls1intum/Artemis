package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * This class is only added to the codebase to support JPA queries in repositories.
 */
@Immutable
@Entity
@Table(name = "user_groups")
public class UserGroup {

    @EmbeddedId
    private UserGroupKey id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "user_groups", insertable = false, updatable = false)
    private String group;

    @Embeddable
    public record UserGroupKey(@Column(name = "user_id") Long userId, @Column(name = "user_groups") String group) {
    }
}
