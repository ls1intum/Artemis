package de.tum.cit.aet.artemis.core.security.policy.definitions;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.SUPER_ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;
import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.hasStarted;
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.memberOfGroup;

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.Conditions;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Policy definitions for programming exercise access control.
 */
@Configuration
@Profile(PROFILE_CORE)
public class ProgrammingExerciseAccessPolicies {

    /**
     * Defines the programming exercise visibility policy.
     * <ul>
     * <li>Teaching assistants, editors, and instructors can see a programming exercise if they are in the course.</li>
     * <li>Admins can always see any programming exercise.</li>
     * <li>Students can see a programming exercise only if they are enrolled and the release date has passed (or is null).</li>
     * <li>All other users are denied access by default.</li>
     * </ul>
     *
     * @return the programming exercise visibility access policy
     */
    @Bean
    @Lazy
    public AccessPolicy<ProgrammingExercise> programmingExerciseVisibilityPolicy() {
        return AccessPolicy.forResource(ProgrammingExercise.class).named("programming-exercise-visibility").section("ProgrammingExercises").feature("View Programming Exercise")
                .rule(when(Conditions.<ProgrammingExercise>isAdmin()).thenAllow().documentedFor(SUPER_ADMIN, ADMIN))
                .rule(when(memberOfGroup(courseGroup(Course::getTeachingAssistantGroupName)).or(memberOfGroup(courseGroup(Course::getEditorGroupName)))
                        .or(memberOfGroup(courseGroup(Course::getInstructorGroupName)))).thenAllow().documentedFor(INSTRUCTOR, EDITOR, TEACHING_ASSISTANT).withNote("if in course"))
                .rule(when(memberOfGroup(courseGroup(Course::getStudentGroupName)).and(hasStarted(ProgrammingExercise::getReleaseDate))).thenAllow().documentedFor(STUDENT)
                        .withNote("if enrolled + released"))
                .denyByDefault();
    }

    /**
     * Helper that extracts a group name from the course associated with a programming exercise.
     * This bridges from exercise -> course -> group name so that {@code memberOfGroup} can be used
     * with exercise-level policies.
     *
     * @param courseGroupExtractor function that extracts a group name from a {@link Course}
     * @return a function that extracts the group name from a programming exercise's course
     */
    private static Function<ProgrammingExercise, String> courseGroup(Function<Course, String> courseGroupExtractor) {
        return exercise -> {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            return course != null ? courseGroupExtractor.apply(course) : null;
        };
    }
}
