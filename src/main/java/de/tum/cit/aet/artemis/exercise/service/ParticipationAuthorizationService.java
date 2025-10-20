package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

/**
 * Service responsible for checking the authorization of users on participations.
 */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class ParticipationAuthorizationService {

    private final AuthorizationCheckService authCheckService;

    private final StudentParticipationRepository studentParticipationRepository;

    public ParticipationAuthorizationService(AuthorizationCheckService authCheckService, StudentParticipationRepository studentParticipationRepository) {
        this.authCheckService = authCheckService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Check if the user has at least instructor permissions in the course of the given participation.
     *
     * @param participation the participation for which the course is checked
     * @param user          the user for which the permissions are checked
     */
    public void checkAccessPermissionAtLeastInstructor(StudentParticipation participation, User user) {
        Course course = findCourseFromParticipation(participation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    /**
     * Find the course from the given participation.
     *
     * @param participation the participation for which the course is searched
     * @return the course of the participation
     */
    public Course findCourseFromParticipation(StudentParticipation participation) {
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }
        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }

    /**
     * Check if the user is the owner of the participation or has at least teaching assistant permissions in the course of the given participation.
     *
     * @param participation the participation for which the access is checked
     * @param user          the user for which the permissions are checked
     */
    public void checkAccessPermissionOwner(StudentParticipation participation, User user) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            Course course = findCourseFromParticipation(participation);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }
}
