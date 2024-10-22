package de.tum.cit.aet.artemis.core.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

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
    public static class UserGroupKey implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "user_groups")
        private String group;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UserGroupKey that = (UserGroupKey) o;

            if (!Objects.equals(userId, that.userId)) {
                return false;
            }
            return Objects.equals(group, that.group);
        }

        @Override
        public int hashCode() {
            int result = userId != null ? userId.hashCode() : 0;
            result = 31 * result + (group != null ? group.hashCode() : 0);
            return result;
        }
    }
}
