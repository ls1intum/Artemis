package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;

/**
 * Service used to check whether user is authorized to perform actions on the entity.
 */
@Service
public class AuthorizationCheckService {

    private final Logger log = LoggerFactory.getLogger(AuthorizationCheckService.class);

    private final UserService userService;

    public AuthorizationCheckService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Given any type of exercise, the method returns if the current user is at least TA for the course the exercise belongs to. If exercise is not present, it will return false,
     * because the optional will be empty, and therefore `isPresent()` will return false This is due how `filter` works: If a value is present, apply the provided mapping function
     * to it, and if the result is non-null, return an Optional describing the result. Otherwise return an empty Optional.
     * https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html#filter-java.util.function.Predicate
     *
     * @param exercise the exercise that needs to be checked
     * @param <T> The type of the concrete exercise, because Exercise is an abstract class
     * @return true if the user is at least a teaching assistant (also if the user is instructor or admin) in the course of the given exercise
     */
    public <T extends Exercise> boolean isAtLeastTeachingAssistantForExercise(Optional<T> exercise) {
        return exercise.filter(this::isAtLeastTeachingAssistantForExercise).isPresent();
    }

    /**
     * checks if the currently logged in user is at least a teaching assistant in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged in user is at least a teaching assistant (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastTeachingAssistantForExercise(Exercise exercise) {
        return isAtLeastTeachingAssistantInCourse(exercise.getCourse(), null);
    }

    /**
     * checks if the passed user is at least a teaching assistant in the course of the given exercise
     *
     * @param exercise the exercise that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastTeachingAssistantForExercise(Exercise exercise, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return isAtLeastTeachingAssistantInCourse(exercise.getCourse(), user);
    }

    /**
     * checks if the currently logged in user is at least a student in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged in user is at least a student (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    public boolean isAtLeastStudentForExercise(Exercise exercise) {
        return isAtLeastStudentForExercise(exercise, null);
    }

    /**
     * checks if the currently logged in user is at least a student in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     @param user the user whose permissions should be checked
     * @return true if the currently logged in user is at least a student (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    public boolean isAtLeastStudentForExercise(Exercise exercise, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return isStudentInCourse(exercise.getCourse(), user) || isAtLeastTeachingAssistantForExercise(exercise, user);
    }

    /**
     * checks if the passed user is at least a teaching assistant in the given course
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant in the course (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastTeachingAssistantInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getInstructorGroupName()) || user.getGroups().contains(course.getTeachingAssistantGroupName()) || isAdmin();
    }

    /**
     * checks if the passed user is at least a teaching assistant in the given course
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant in the course (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastStudentInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getInstructorGroupName()) || user.getGroups().contains(course.getTeachingAssistantGroupName())
                || user.getGroups().contains(course.getStudentGroupName()) || isAdmin();
    }

    /**
     * checks if the currently logged in user is at least an instructor in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user the user whose permissions should be checked
     * @return true if the currently logged in user is at least an instructor (or admin), false otherwise
     */
    public boolean isAtLeastInstructorForExercise(Exercise exercise, User user) {
        return isAtLeastInstructorInCourse(exercise.getCourse(), user);
    }

    /**
     * checks if the currently logged in user is at least an instructor in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged in user is at least an instructor (or admin), false otherwise
     */
    public boolean isAtLeastInstructorForExercise(Exercise exercise) {
        return isAtLeastInstructorForExercise(exercise, null);
    }

    /**
     * checks if the passed user is at least instructor in the given course
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true if the passed user is at least instructor in the course (also if the user is admin), false otherwise
     */
    public boolean isAtLeastInstructorInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getInstructorGroupName()) || isAdmin();
    }

    /**
     * checks if the passed user is instructor in the given course
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true, if user is instructor of this course, otherwise false
     */
    public boolean isInstructorInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getInstructorGroupName());
    }

    /**
     * checks if the currently logged in user is teaching assistant of this course
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true, if user is teaching assistant of this course, otherwise false
     */
    public boolean isTeachingAssistantInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getTeachingAssistantGroupName());
    }

    /**
     * checks if the currently logged in user is only a student of this course. This means the user is NOT a tutor, NOT an instructor and NOT an ADMIN
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true, if user is only student of this course, otherwise false
     */
    public boolean isOnlyStudentInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getStudentGroupName()) && !isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * checks if the currently logged in user is student in the given course
     *
     * @param course the course that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true, if user is student of this course, otherwise false
     */
    public boolean isStudentInCourse(Course course, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        return user.getGroups().contains(course.getStudentGroupName());
    }

    /**
     * checks if the currently logged in user is owner of the given participation
     *
     * @param participation the participation that needs to be checked
     * @return true, if user is student is owner of this participation, otherwise false
     */
    public boolean isOwnerOfParticipation(StudentParticipation participation) {
        if (participation.getStudent() == null) {
            return false;
        }
        else {
            return participation.getStudent().getLogin().equals(SecurityUtils.getCurrentUserLogin().get());
        }
    }

    /**
     * checks if the currently logged in user is owner of the given participation
     *
     * @param participation the participation that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true, if user is student is owner of this participation, otherwise false
     */
    public boolean isOwnerOfParticipation(StudentParticipation participation, User user) {
        if (user == null || user.getGroups() == null) {
            // only retrieve the user and the groups if the user is null or the groups are missing (to save performance)
            user = userService.getUserWithGroupsAndAuthorities();
        }
        if (participation.getStudent() == null) {
            return false;
        }
        else {
            return participation.getStudent().equals(user);
        }
    }

    /**
     * Method used to check whether the user of the websocket message is owner of this participation
     *
     * @param participation participation to check the rights for
     * @param principal     a representation of the currently logged in user
     * @return true, if user is student is owner of this participation, otherwise false
     */
    public boolean isOwnerOfParticipation(StudentParticipation participation, Principal principal) {
        return participation.getStudent() != null && participation.getStudent().getLogin().equals(principal.getName());
    }

    /**
     * checks if the passed user is allowed to see the given exercise, i.e. if the passed user is at least a student in the course
     *
     * @param exercise the exercise that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true, if user is allowed to see this exercise, otherwise false
     */
    public boolean isAllowedToSeeExercise(Exercise exercise, User user) {
        if (isAdmin()) {
            return true;
        }
        if (user == null || user.getGroups() == null) {
            user = userService.getUserWithGroupsAndAuthorities();
        }
        Course course = exercise.getCourse();
        return isInstructorInCourse(course, user) || isTeachingAssistantInCourse(course, user) || (isStudentInCourse(course, user) && exercise.isVisibleToStudents());
    }

    /**
     * checks if the currently logged in user is an admin user
     *
     * @return true, if user is admin, otherwise false
     */
    public boolean isAdmin() {
        return SecurityUtils.isCurrentUserInRole(AuthoritiesConstants.ADMIN);
    }

    /**
     * Checks if the currently logged in user is allowed to retrieve the given result.
     * The user is allowed to retrieve the result if (s)he is at least a student in the corresponding course, the
     * submission is his/her submission, the assessment due date of the corresponding exercise is in the past (or not set) and the result is finished.
     *
     * @param exercise      the corresponding exercise
     * @param participation the participation the result belongs to
     * @param result        the result that should be sent to the client
     * @return true if the user is allowed to retrieve the given result, false otherwise
     */
    public boolean isUserAllowedToGetResult(Exercise exercise, StudentParticipation participation, Result result) {
        return isAtLeastStudentForExercise(exercise) && isOwnerOfParticipation(participation)
                && (exercise.getAssessmentDueDate() == null || exercise.getAssessmentDueDate().isBefore(ZonedDateTime.now())) && result.getAssessor() != null
                && result.getCompletionDate() != null;
    }
}
