package de.tum.cit.aet.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * The primary key for ConductAgreement
 */
public class ConductAgreementId implements Serializable {

    private Long course;

    private Long user;

    ConductAgreementId(Long course, Long user) {
        this.course = course;
        this.user = user;
    }

    ConductAgreementId() {
        // Needed for JPA
    }

    public Long getCourse() {
        return course;
    }

    public void setCourse(Long course) {
        this.course = course;
    }

    public Long getUser() {
        return user;
    }

    public void setUser(Long user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConductAgreementId that = (ConductAgreementId) o;
        return course.equals(that.course) && user.equals(that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(course, user);
    }
}
