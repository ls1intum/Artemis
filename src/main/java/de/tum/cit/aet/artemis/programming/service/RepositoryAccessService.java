package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismAccessApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Service for checking if a user has access to a repository.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class RepositoryAccessService {

    private final Optional<PlagiarismAccessApi> plagiarismAccessApi;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseDateService exerciseDateService;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    public RepositoryAccessService(Optional<PlagiarismAccessApi> plagiarismAccessApi, AuthorizationCheckService authorizationCheckService, ExerciseDateService exerciseDateService,
            ParticipationAuthorizationCheckService participationAuthorizationCheckService) {
        this.plagiarismAccessApi = plagiarismAccessApi;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseDateService = exerciseDateService;
        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
    }

    /**
     * Checks if the user has access to the repository of the given participation.
     * Throws an {@link AccessForbiddenException} otherwise.
     *
     * @param programmingParticipation The participation for which the repository should be accessed.
     * @param user                     The user who wants to access the repository.
     * @param programmingExercise      The programming exercise of the participation with the submission policy set.
     * @param repositoryActionType     The type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     * @throws AccessForbiddenException If the user is not allowed to access the repository.
     */
    public void checkAccessRepositoryElseThrow(ProgrammingExerciseParticipation programmingParticipation, User user, ProgrammingExercise programmingExercise,
            RepositoryActionType repositoryActionType) throws AccessForbiddenException {
        boolean isAtLeastEditor = authorizationCheckService.isAtLeastEditorInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean isTeachingAssistant = authorizationCheckService.isTeachingAssistantInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean atLeastStudent = authorizationCheckService.isAtLeastStudentInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);

        // The user is allowed to access the repository in any way if they are at least an editor or if they are a teaching assistant and only want to read the repository.
        if (isAtLeastEditor || (isTeachingAssistant && repositoryActionType == RepositoryActionType.READ)) {
            return;
        }

        // The user has to be at least a student in the course to access the repository.
        // We also check if the user is the owner of the participation and if it's a student participation.
        if (atLeastStudent && programmingParticipation instanceof ProgrammingExerciseStudentParticipation programmingStudentParticipation) {
            boolean ownerOfParticipation = authorizationCheckService.isOwnerOfParticipation(programmingStudentParticipation, user);
            if (ownerOfParticipation) {
                if (hasAccessToOwnStudentParticipation(programmingExercise, repositoryActionType, programmingStudentParticipation, isTeachingAssistant)) {
                    return;
                }
            }
        }

        throw new AccessForbiddenException("You are not allowed to access the repository of this programming exercise.");
    }

    /**
     * Helper method to check if the student or teaching assistant has access to their own student participation.
     *
     * @param programmingExercise  The programming exercise.
     * @param repositoryActionType The type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     * @param studentParticipation The student participation.
     * @param isTeachingAssistant  True if the user is a teaching assistant, false otherwise.
     * @return True if the user has access to the repository, false otherwise.
     */
    private boolean hasAccessToOwnStudentParticipation(ProgrammingExercise programmingExercise, RepositoryActionType repositoryActionType,
            ProgrammingExerciseStudentParticipation studentParticipation, boolean isTeachingAssistant) {
        boolean hasStarted = exerciseDateService.hasExerciseStarted(programmingExercise);

        if (!hasStarted) {
            // Only teaching assistants have access to the repository before the exercise has started.
            return isTeachingAssistant;
        }

        // The user always has read permissions after the exercise has started.
        if (repositoryActionType == RepositoryActionType.READ) {
            return true;
        }

        if (participationAuthorizationCheckService.isLocked(studentParticipation, programmingExercise)) {
            // The user does not have write or reset permissions if the participation is locked.
            return false;
        }

        // Check if the user has write or reset permissions.
        return hasWriteOrResetPermissionsForUnlockedParticipation(studentParticipation);
    }

    /**
     * Helper method that checks if the student or teaching assistant has write or reset permissions
     * for the given programming participation.
     *
     * @param studentParticipation The student participation.
     * @return True if the user has write or reset permissions, false otherwise.
     */
    private boolean hasWriteOrResetPermissionsForUnlockedParticipation(StudentParticipation studentParticipation) {
        boolean beforeDueDate = exerciseDateService.isBeforeDueDate(studentParticipation);
        boolean isPracticeMode = studentParticipation.isPracticeMode();

        // The user has write or reset permissions if the due date has not passed yet.
        if (beforeDueDate) {
            return true;
        }

        // The user has write or reset permissions if due date has passed, but the participation is in practice mode.
        return isPracticeMode;
    }

    /**
     * Checks if the user has access to the test repository of the given programming exercise.
     * Throws an {@link AccessForbiddenException} otherwise.
     *
     * @param atLeastEditor  if true, the user needs at least editor permissions, otherwise only teaching assistant permissions are required.
     * @param exercise       the programming exercise the test repository belongs to.
     * @param user           the user that wants to access the test repository.
     * @param repositoryType the type of the repository.
     */
    public void checkAccessTestOrAuxRepositoryElseThrow(boolean atLeastEditor, ProgrammingExercise exercise, User user, String repositoryType) {
        if (atLeastEditor) {
            if (!authorizationCheckService.isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("You are not allowed to push to the " + repositoryType + " repository of this programming exercise.");
            }
        }
        else {
            if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("You are not allowed to access the " + repositoryType + " repository of this programming exercise.");
            }
        }
    }

    /**
     * Checks if the user has access to the plagiarism submission of the given programming participation.
     *
     * @param programmingParticipation The participation for which the plagiarism submission should be accessed.
     * @param user                     The user who wants to access the plagiarism submission.
     * @param repositoryActionType     The type of action that the user wants to perform on the plagiarism submission (i.e. READ, WRITE or RESET).
     * @throws AccessForbiddenException If the user is not allowed to access the plagiarism submission.
     */
    public void checkHasAccessToPlagiarismSubmission(ProgrammingExerciseParticipation programmingParticipation, User user, RepositoryActionType repositoryActionType)
            throws AccessForbiddenException {
        if (repositoryActionType == RepositoryActionType.READ) {
            boolean isAtLeastTeachingAssistant = authorizationCheckService
                    .isAtLeastTeachingAssistantInCourse(programmingParticipation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember(), user);
            if (isAtLeastTeachingAssistant) {
                return;
            }
            if (plagiarismAccessApi.isEmpty()
                    || plagiarismAccessApi.get().hasAccessToSubmission(programmingParticipation.getId(), user.getLogin(), (Participation) programmingParticipation)) {
                return;
            }
        }
        throw new AccessForbiddenException("You are not allowed to access the plagiarism result of this programming exercise.");
    }

    public void checkHasAccessToOfflineIDEElseThrow(ProgrammingExercise exercise, User user) throws AccessForbiddenException {
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde()) && authorizationCheckService.isOnlyStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
            throw new AccessForbiddenException();
        }
    }

    public boolean checkHasAccessToForcePush(ProgrammingExercise exercise, User user, String repositoryTypeOrUserName) {
        boolean isAllowedRepository = repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())
                || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString());

        return isAllowedRepository && authorizationCheckService.isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

}
