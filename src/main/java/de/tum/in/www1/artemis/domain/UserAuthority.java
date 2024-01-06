package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

@Entity
@Table(name = "jhi_user_authority")
public class UserAuthority {

    @EmbeddedId
    private UserAuthorityKey id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "authority_name", insertable = false, updatable = false)
    private String authorityName;

    @Embeddable
    public static class UserAuthorityKey implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "authority_name")
        private String authorityName;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UserAuthorityKey that = (UserAuthorityKey) o;

            if (!Objects.equals(userId, that.userId)) {
                return false;
            }
            return Objects.equals(authorityName, that.authorityName);
        }

        @Override
        public int hashCode() {
            int result = userId != null ? userId.hashCode() : 0;
            result = 31 * result + (authorityName != null ? authorityName.hashCode() : 0);
            return result;
        }
    }
}
