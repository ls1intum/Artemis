package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

/**
 * The primary key for CourseCodeOfConductAgreement
 *
 * @param course the course's id
 * @param user   the user's id
 */
record CourseCodeOfConductAgreementId(Long course, Long user) implements Serializable {

    // Needed for JPA
    CourseCodeOfConductAgreementId() {
        this(null, null);
    }
}
