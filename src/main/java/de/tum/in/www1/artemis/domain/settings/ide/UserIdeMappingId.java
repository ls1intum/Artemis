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

    // @Override
    // public boolean equals(Object o) {
    // if (this == o) {
    // return true;
    // }
    // if (o == null || getClass() != o.getClass()) {
    // return false;
    // }
    //
    // UserIdeMappingId that = (UserIdeMappingId) o;
    //
    // if (!user.equals(that.user)) {
    // return false;
    // }
    // return programmingLanguage == that.programmingLanguage;
    // }
    //
    // @Override
    // public int hashCode() {
    // int result = user.hashCode();
    // result = 31 * result + programmingLanguage.hashCode();
    // return result;
    // }
}
