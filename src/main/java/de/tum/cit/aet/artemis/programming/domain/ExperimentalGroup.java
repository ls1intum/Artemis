package de.tum.cit.aet.artemis.programming.domain;

public enum ExperimentalGroup {
    /**
     * No nudging technique is used
     */
    CONTROL,
    /**
     * User gets visual nudges in the Artemis platform
     */
    VISUAL,
    /**
     * User gets email notifications if using password-based authentication
     */
    EMAIL,
    /**
     * User gets both visual nudges and emails
     */
    OMNICHANNEL
}
