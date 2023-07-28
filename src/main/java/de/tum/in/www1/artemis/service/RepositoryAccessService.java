package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.AccessUnauthorizedException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

/**
 * Service for checking if a user has access to a repository.
 */
@Service
public class RepositoryAccessService {

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final PlagiarismService plagiarismService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExamSubmissionService examSubmissionService;

    private final ExerciseDateService exerciseDateService;

    private final SubmissionPolicyService submissionPolicyService;

    public RepositoryAccessService(ParticipationAuthorizationCheckService participationAuthCheckService, PlagiarismService plagiarismService,
            AuthorizationCheckService authorizationCheckService, ExamSubmissionService examSubmissionService, ExerciseDateService exerciseDateService,
            SubmissionPolicyService submissionPolicyService) {
        this.participationAuthCheckService = participationAuthCheckService;
        this.plagiarismService = plagiarismService;
        this.authorizationCheckService = authorizationCheckService;
        this.examSubmissionService = examSubmissionService;
        this.exerciseDateService = exerciseDateService;
        this.submissionPolicyService = submissionPolicyService;
    }

    /**
     * Checks if the user has access to the repository of the given participation.
     * Throws an {@link AccessForbiddenException} otherwise.
     *
     * @param programmingParticipation The participation for which the repository should be accessed.
     * @param user                     The user who wants to access the repository.
     * @param programmingExercise      The programming exercise of the participation with the submission policy set.
     * @param repositoryActionType     The type of action that the user wants to perform on the repository (i.e. WRITE or READ).
     */
    public void checkAccessRepositoryElseThrow(ProgrammingExerciseParticipation programmingParticipation, User user, ProgrammingExercise programmingExercise,
            RepositoryActionType repositoryActionType) {

        // Error case 1: The user does not have permissions to push into the repository and the user is not notified for a related plagiarism case.
        boolean hasPermissions = participationAuthCheckService.canAccessParticipation(programmingParticipation, user);
        boolean userWasNotifiedAboutPlagiarismCase = plagiarismService.wasUserNotifiedByInstructor(programmingParticipation.getId(), user.getLogin());
        if (!hasPermissions && !userWasNotifiedAboutPlagiarismCase) {
            throw new AccessUnauthorizedException();
        }

        boolean isAtLeastEditor = authorizationCheckService.isAtLeastEditorInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean isStudent = authorizationCheckService.isOnlyStudentInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean isTeachingAssistant = !isStudent && !isAtLeastEditor;

        // Error case 2: The user's participation is locked.
        // Editors and up are able to push to any repository even if the participation is locked for the student.
        // Teaching assistants trying to push to a student assignment repository will be blocked by the next check.
        if (repositoryActionType == RepositoryActionType.WRITE && isStudent && programmingParticipation.isLocked()) {
            // Return a message to the client.
            String errorMessage;
            if (!programmingExercise.isReleased()) {
                errorMessage = "submitBeforeStartDate";
            }
            else if (exerciseDateService.isAfterDueDate(programmingParticipation)) {
                errorMessage = "submitAfterDueDate";
            }
            else if (programmingExercise.getSubmissionPolicy() instanceof LockRepositoryPolicy lockRepositoryPolicy
                    && lockRepositoryPolicy.getSubmissionLimit() <= submissionPolicyService.getParticipationSubmissionCount((Participation) programmingParticipation)) {
                errorMessage = "submitAfterReachingSubmissionLimit";
            }
            else {
                throw new IllegalStateException("The participation is locked but the reason is unknown.");
            }

            throw new AccessForbiddenException(errorMessage);
        }

        // Error case 3: A teaching assistant tries to push into a base repository (in that case the participation is not a StudentParticipation) or into a student assignment
        // repository (in that case the teaching assistant does not own the participation).
        boolean isStudentParticipation = programmingParticipation instanceof StudentParticipation;
        if (isTeachingAssistant && repositoryActionType == RepositoryActionType.WRITE
                && (!isStudentParticipation || !((StudentParticipation) programmingParticipation).isOwnedBy(user))) {
            throw new AccessUnauthorizedException();
        }

        // Error case 4: The student can reset the repository only before and a tutor/instructor only after the due date has passed
        if (repositoryActionType == RepositoryActionType.RESET) {
            checkAccessRepositoryForReset(programmingParticipation, isStudent, programmingExercise);
        }

        // Error case 5: Before or after exam working time, students are not allowed to read or submit to the repository for an exam exercise. Teaching assistants are only allowed
        // to read the student's repository.
        // But the student should still be able to access if they are notified for a related plagiarism case.
        if ((isStudent || (isTeachingAssistant && repositoryActionType != RepositoryActionType.READ))
                && !examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, user, false) && !userWasNotifiedAboutPlagiarismCase) {
            throw new AccessForbiddenException();
        }
    }

    /*
     * The student can reset the repository only before and a tutor/instructor only after the due date has passed
     */
    private void checkAccessRepositoryForReset(ProgrammingExerciseParticipation programmingExerciseParticipation, boolean isStudent, ProgrammingExercise programmingExercise) {
        boolean isOwner = true; // true for Solution- and TemplateProgrammingExerciseParticipation
        if (programmingExerciseParticipation instanceof StudentParticipation studentParticipation) {
            isOwner = authorizationCheckService.isOwnerOfParticipation(studentParticipation);
        }
        if (isStudent && programmingExerciseParticipation.isLocked()) {
            throw new AccessForbiddenException();
        }
        // A tutor/instructor who is owner of the exercise should always be able to reset the repository
        else if (!isStudent && !isOwner) {
            // The user trying to reset the repository is at least a tutor in the course and the repository does not belong to them.
            // This might be a tutor that resets the repository during assessment.
            // If the student is still able to submit, don't allow for the tutor to reset the repository.

            // For a regular course exercise, the participation must be locked.
            if (programmingExercise.isCourseExercise() && !programmingExerciseParticipation.isLocked()) {
                throw new AccessForbiddenException();
            }

            // For an exam exercise, the student must not be allowed to submit anymore.
            // Retrieving the student via this type cast is safe because isOwner is false here which means that "programmingExerciseParticipation instanceof StudentParticipation"
            // must have been true in the check above.
            var optStudent = ((StudentParticipation) programmingExerciseParticipation).getStudent();
            if (optStudent.isPresent() && programmingExercise.isExamExercise() && examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, optStudent.get(), false)) {
                throw new AccessForbiddenException();
            }
        }
    }

    /**
     * Checks if the user has access to the test repository of the given programming exercise.
     * Throws an {@link AccessForbiddenException} otherwise.
     *
     * @param atLeastEditor if true, the user needs at least editor permissions, otherwise only teaching assistant permissions are required.
     * @param exercise      the programming exercise the test repository belongs to.
     * @param user          the user that wants to access the test repository.
     */
    public void checkAccessTestRepositoryElseThrow(boolean atLeastEditor, ProgrammingExercise exercise, User user) {
        if (atLeastEditor) {
            if (!authorizationCheckService.isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("You are not allowed to access the test repository of this programming exercise.");
            }
        }
        else {
            if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("You are not allowed to push to the test repository of this programming exercise.");
            }
        }
    }
}
