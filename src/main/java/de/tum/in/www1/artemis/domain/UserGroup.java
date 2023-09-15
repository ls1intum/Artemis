package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

@Entity
@Table(name = "user_groups")
public class UserGroup {

    @EmbeddedId
    private UserGroupKey id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "`groups`", insertable = false, updatable = false)
    private String group;

    @Embeddable
    public static class UserGroupKey implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "`groups`")
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
