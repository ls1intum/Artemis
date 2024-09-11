package de.tum.cit.aet.artemis.domain.settings.ide;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;

@Entity
@Table(name = "user_ide_mapping")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class UserIdeMapping {

    @EmbeddedId
    private UserIdeMappingId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JsonIgnore
    private User user;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ide_id", nullable = false)
    private Ide ide;

    public UserIdeMapping() {
        // empty constructor for Jackson
        // public for server tests
    }

    public UserIdeMapping(User user, ProgrammingLanguage programmingLanguage, Ide ide) {
        this.id = new UserIdeMappingId(user.getId(), programmingLanguage);
        this.user = user;
        this.ide = ide;
    }

    public void setId(UserIdeMappingId id) {
        this.id = id;
    }

    public void setIde(Ide ide) {
        this.ide = ide;
    }

    public UserIdeMappingId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return id.programmingLanguage;
    }

    public Ide getIde() {
        return ide;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        UserIdeMapping that = (UserIdeMapping) obj;
        if (id == null) {
            return false;
        }

        return id.equals(that.getId());
    }

    @Embeddable
    public static class UserIdeMappingId implements Serializable {

        private Long userId;

        @Column(name = "programming_language", nullable = false)
        @Enumerated(EnumType.STRING)
        private ProgrammingLanguage programmingLanguage;

        public UserIdeMappingId() {
            // empty constructor for Jackson
            // public for server tests
        }

        public UserIdeMappingId(Long userId, ProgrammingLanguage programmingLanguage) {
            this.userId = userId;
            this.programmingLanguage = programmingLanguage;
        }

        public Long getUserId() {
            return userId;
        }

        public ProgrammingLanguage getProgrammingLanguage() {
            return programmingLanguage;
        }

        public void setProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
            this.programmingLanguage = programmingLanguage;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, programmingLanguage);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            UserIdeMappingId that = (UserIdeMappingId) obj;
            if (userId == null || programmingLanguage == null) {
                return false;
            }

            return userId.equals(that.userId) && programmingLanguage.equals(that.programmingLanguage);
        }
    }
}
