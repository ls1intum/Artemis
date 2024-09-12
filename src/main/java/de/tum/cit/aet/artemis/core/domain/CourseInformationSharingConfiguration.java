package de.tum.cit.aet.artemis.core.domain;

/**
 * NOTE: Ordinal mapping in property of Course.java do NOT change the order of the existing values.
 */
public enum CourseInformationSharingConfiguration {

    /**
     * Both Communication and Messaging are disabled VALUE = 0
     */
    DISABLED,

    /**
     * Both Communication and Messaging are enabled VALUE = 1
     */
    COMMUNICATION_AND_MESSAGING,
    /**
     * Only Communication is enabled VALUE = 2
     */
    COMMUNICATION_ONLY;

    public boolean isMessagingEnabled() {
        return this == COMMUNICATION_AND_MESSAGING;
    }
}
