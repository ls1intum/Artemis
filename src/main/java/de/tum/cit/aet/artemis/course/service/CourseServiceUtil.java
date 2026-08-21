package de.tum.cit.aet.artemis.course.service;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Utility class for course-related services.
 */
public final class CourseServiceUtil {

    private CourseServiceUtil() {
        // Utility class, no instances allowed
    }

    /**
     * Helper method which removes some values from the user entity which are not needed in the client
     *
     * @param usersInGroup user whose variables are removed
     */
    static void removeUserVariables(Iterable<User> usersInGroup) {
        usersInGroup.forEach(user -> {
            // The activation key is no longer on the user, so there is nothing to strip here any more.
            user.setLangKey(null);
            user.setCreatedDate(null);
        });
    }
}
