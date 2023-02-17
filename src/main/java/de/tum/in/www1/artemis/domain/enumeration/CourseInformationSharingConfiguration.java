package de.tum.in.www1.artemis.domain.enumeration;

public enum CourseInformationSharingConfiguration {

    /**
     * Both Communication and Messaging are enabled
     * <p>
     * Note: If changed you need update the migration sql in 20230217110200_changelog.xml
     */
    COMMUNICATION_AND_MESSAGING,
    /**
     * Only Communication is enabled
     */
    COMMUNICATION_ONLY,
    /**
     * Only Messaging is enabled
     */
    MESSAGING_ONLY,
    /**
     * Both Communication and Messaging are disabled
     */
    DISABLED;
}
