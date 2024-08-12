package de.tum.in.www1.artemis.domain.settings.ide;

import java.io.Serializable;

import jakarta.persistence.IdClass;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@IdClass(UserIdeMappingId.class)
public class UserIdeMappingId implements Serializable {

    private Long user;

    private ProgrammingLanguage programmingLanguage;

    protected UserIdeMappingId() {
        // empty constructor for Jackson
    }

    public UserIdeMappingId(Long user, ProgrammingLanguage programmingLanguage) {
        this.user = user;
        this.programmingLanguage = programmingLanguage;
    }

    public Long getUser() {
        return user;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public void setProgrammingLanguage(ProgrammingLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public void setUser(Long user) {
        this.user = user;
    }
}
