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

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ParticipationAuthorizationHelper {

    private final AuthorizationCheckService authCheckService;

    private final StudentParticipationRepository studentParticipationRepository;

    public ParticipationAuthorizationHelper(AuthorizationCheckService authCheckService, StudentParticipationRepository studentParticipationRepository) {
        this.authCheckService = authCheckService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    public void checkAccessPermissionAtLeastInstructor(StudentParticipation participation, User user) {
        Course course = findCourseFromParticipation(participation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    public Course findCourseFromParticipation(StudentParticipation participation) {
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }
        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }

    public void checkAccessPermissionOwner(StudentParticipation participation, User user) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            Course course = findCourseFromParticipation(participation);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }
}
