package de.tum.cit.aet.artemis.core.service.course;

import de.tum.cit.aet.artemis.core.domain.User;

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
            user.setActivationKey(null);
            user.setLangKey(null);
            user.setCreatedDate(null);
        });
    }
}
