package de.tum.in.www1.artemis.domain.competency;

public enum RelationType {
    /**
     * A generic relation between two competencies.
     */
    RELATES,
    /**
     * The tail competency assumes that the student already achieved the head competency.
     */
    ASSUMES,
    /**
     * The tail competency extends the head competency on the same topic in more detail.
     */
    EXTENDS,
    /**
     * The tail competency matches the head competency (e.g., a duplicate).
     */
    MATCHES
}
