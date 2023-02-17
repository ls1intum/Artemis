package de.tum.in.www1.artemis.domain.enumeration;

import de.tum.in.www1.artemis.domain.Course;

public enum CourseInformationSharingConfiguration {

    /**
     * Both Communication and Messaging are enabled
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

    public static boolean isCommunicationEnabled(Course course) {
        var config = course.getCourseInformationSharingConfiguration();
        return config == COMMUNICATION_AND_MESSAGING || config == COMMUNICATION_ONLY;
    }

    public static boolean isMessagingEnabled(Course course) {
        var config = course.getCourseInformationSharingConfiguration();
        return config == COMMUNICATION_AND_MESSAGING || config == MESSAGING_ONLY;
    }
}
