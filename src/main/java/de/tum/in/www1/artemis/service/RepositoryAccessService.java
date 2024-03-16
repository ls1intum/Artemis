package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

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
        else if (atLeastStudent) {
            boolean hasStarted = exerciseDateService.hasExerciseStarted(programmingExercise);

            if (hasStarted) {
                if (hasAccessAfterExerciseStart(programmingParticipation, repositoryActionType))
                    return;
            }
            else if (isTeachingAssistant)
                return;
        }

        throw new AccessForbiddenException("You are not allowed to access the repository of this programming exercise.");
    }

    /**
     * Checks if the student or teaching assistant has access to the repository of the given participation after the exercise has started.
     *
     * @param programmingParticipation The participation for which the repository should be accessed.
     * @param repositoryActionType     The type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     * @return True if the user has access to the repository, false otherwise.
     */
    private boolean hasAccessAfterExerciseStart(ProgrammingExerciseParticipation programmingParticipation, RepositoryActionType repositoryActionType) {
        // The user in this case can be a student or a teaching assistant.
        // We need to check if it is a student participation and if the user is the owner of the participation.
        // The case READ of the teaching assistant is already handled in the checkAccessRepositoryElseThrow method.
        if (programmingParticipation instanceof StudentParticipation studentParticipation && authorizationCheckService.isOwnerOfParticipation(studentParticipation)) {
            // The user always has read permissions after the exercise has started.
            if (repositoryActionType == RepositoryActionType.READ)
                return true;

            // Check if the user has write or reset permissions.
            else
                return hasWriteOrResetPermissions(programmingParticipation.isLocked(), studentParticipation);

        }
        return false;
    }

    /**
     * Checks if the user has write or reset permissions for the given programming participation.
     * The user in this case can be a student or a teaching assistant.
     *
     * @param locked               True if the participation is locked, false otherwise.
     * @param studentParticipation The student participation.
     * @return True if the user has write or reset permissions, false otherwise.
     */
    private boolean hasWriteOrResetPermissions(boolean locked, StudentParticipation studentParticipation) {
        boolean beforeDueDate = exerciseDateService.isBeforeDueDate(studentParticipation);
        boolean isPracticeMode = studentParticipation.isPracticeMode();

        // The user has write or reset permissions if the participation is not locked and the due date has not passed yet.
        if (beforeDueDate && !locked)
            return true;

        // The user has write or reset permissions if due date has passed, but the participation is in practice mode.
        if (exerciseDateService.isAfterDueDate(studentParticipation) && isPracticeMode) {
            return true;
        }

        return false;
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
