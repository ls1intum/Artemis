package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Service for checking if a user has access to a repository.
 */
@Profile(PROFILE_CORE)
@Service
public class RepositoryAccessService {

    private final PlagiarismService plagiarismService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseDateService exerciseDateService;

    public RepositoryAccessService(PlagiarismService plagiarismService, AuthorizationCheckService authorizationCheckService, ExerciseDateService exerciseDateService) {
        this.plagiarismService = plagiarismService;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseDateService = exerciseDateService;
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
        if (atLeastStudent && programmingParticipation instanceof StudentParticipation studentParticipation) {
            boolean ownerOfParticipation = authorizationCheckService.isOwnerOfParticipation(studentParticipation, user);
            if (ownerOfParticipation) {
                if (hasAccessToOwnStudentParticipation(programmingExercise, repositoryActionType, studentParticipation, isTeachingAssistant, programmingParticipation.isLocked())) {
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
     * @param isLocked             True if the participation is locked, false otherwise.
     * @return True if the user has access to the repository, false otherwise.
     */
    private boolean hasAccessToOwnStudentParticipation(ProgrammingExercise programmingExercise, RepositoryActionType repositoryActionType,
            StudentParticipation studentParticipation, boolean isTeachingAssistant, boolean isLocked) {
        boolean hasStarted = exerciseDateService.hasExerciseStarted(programmingExercise);

        if (!hasStarted) {
            // Only teaching assistants have access to the repository before the exercise has started.
            return isTeachingAssistant;
        }

        return hasAccessAfterExerciseStart(studentParticipation, repositoryActionType, isLocked);
    }

    /**
     * Checks if the student or teaching assistant has access to the repository of the given participation after the exercise has started.
     *
     * @param studentParticipation The student participation.
     * @param repositoryActionType The type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     * @return True if the user has access to the repository, false otherwise.
     */
    private boolean hasAccessAfterExerciseStart(StudentParticipation studentParticipation, RepositoryActionType repositoryActionType, boolean isLocked) {
        // The user always has read permissions after the exercise has started.
        if (repositoryActionType == RepositoryActionType.READ) {
            return true;
        }
        if (isLocked) {
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
            if (plagiarismService.hasAccessToSubmission(programmingParticipation.getId(), user.getLogin(), (Participation) programmingParticipation)) {
                return;
            }
        }
        throw new AccessForbiddenException("You are not allowed to access the plagiarism result of this programming exercise.");
    }

}
