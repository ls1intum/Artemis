package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import javax.annotation.Nullable;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.service.authorization.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.AccessUnauthorizedException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

/**
 * Service for checking if a user has access to a repository.
 */
@Profile(PROFILE_CORE)
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
     * @param repositoryActionType     The type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     */
    public void checkAccessRepositoryElseThrow(ProgrammingExerciseParticipation programmingParticipation, User user, ProgrammingExercise programmingExercise,
            RepositoryActionType repositoryActionType) {
        boolean isAtLeastEditor = authorizationCheckService.isAtLeastEditorInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean isStudent = authorizationCheckService.isOnlyStudentInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean isTeachingAssistant = authorizationCheckService.isTeachingAssistantInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        boolean isStudentParticipation = programmingParticipation instanceof StudentParticipation;

        // NOTE: the exerciseStartDate can be null, e.g. if no release and no start is defined for a course exercise
        @Nullable
        ZonedDateTime exerciseStartDate = programmingExercise.getParticipationStartDate();

        // The user is allowed to access the repository in any way if they are at least an editor or if they are a teaching assistant and only want to read the repository.
        if (isAtLeastEditor || (isTeachingAssistant && repositoryActionType == RepositoryActionType.READ)) {
            return;
        }

        // Error case 1: The user does not have permissions to access the participation or the submission for plagiarism comparison.
        checkHasParticipationPermissionsOrCanAccessSubmissionForPlagiarismComparisonElseThrow(programmingParticipation, user, programmingExercise);

        // Error case 2: The user's participation is locked.
        checkIsTryingToAccessLockedParticipation(programmingParticipation, programmingExercise, repositoryActionType);

        // Error case 3: A user tries to push into a base repository (in that case the participation is not a StudentParticipation but Template or Solution)
        if (repositoryActionType == RepositoryActionType.WRITE && !isStudentParticipation) {
            throw new AccessUnauthorizedException();
        }

        // Error case 4: A student tries to push into a repository that does not belong to them.
        if (repositoryActionType == RepositoryActionType.WRITE && !((StudentParticipation) programmingParticipation).isOwnedBy(user)) {
            throw new AccessUnauthorizedException();
        }

        // Error case 5: The student can reset the repository only before and a tutor/instructor only after the due date has passed
        if (repositoryActionType == RepositoryActionType.RESET) {
            checkAccessRepositoryForReset(programmingParticipation, isStudent, programmingExercise);
        }

        // Error case 6: Check if the user is allowed to access the repository concerning the dates
        if (isStudent) {
            checkIsStudentAllowedToAccessRepositoryConcerningDates(programmingParticipation, repositoryActionType, exerciseStartDate);
        }

        // Error case 7: Check if the user is allowed to submit for the exam
        if (repositoryActionType == RepositoryActionType.WRITE && !examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, user, false)) {
            throw new AccessForbiddenException();
        }
    }

    /**
     * Checks if the user has access to repository concerning the dates.
     *
     * @param programmingParticipation The participation for which the repository should be accessed.
     * @param repositoryActionType     The type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     * @param exerciseStartDate        The start date of the exercise.
     */
    private void checkIsStudentAllowedToAccessRepositoryConcerningDates(ProgrammingExerciseParticipation programmingParticipation, RepositoryActionType repositoryActionType,
            @Nullable ZonedDateTime exerciseStartDate) {
        // if the exercise has not started yet, the student is not allowed to access the repository
        if (exerciseStartDate != null && exerciseStartDate.isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException();
        }
        else {
            // if the exercise has started, the student is allowed to access the repository for read actions
            // for the other actions, the student is only allowed to access the repository if the due date has not passed yet
            // or the student is in practice mode
            if (repositoryActionType != RepositoryActionType.READ && exerciseDateService.isAfterDueDate(programmingParticipation)
                    && !((StudentParticipation) programmingParticipation).isPracticeMode()) {
                throw new AccessForbiddenException();
            }
        }
    }

    private void checkIsTryingToAccessLockedParticipation(ProgrammingExerciseParticipation programmingParticipation, ProgrammingExercise programmingExercise,
            RepositoryActionType repositoryActionType) {
        if (repositoryActionType == RepositoryActionType.WRITE && programmingParticipation.isLocked()) {
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
    }

    /**
     * Checks if the user has permissions to access the participation.
     * If not the there needs to exist a plagiarism comparison for the participation and the user needs to have access to the submission.
     * If the user does have permissions for accessing the participation, the user needs to have access to the submission for plagiarism comparison if one exists.
     * Throws an {@link AccessForbiddenException} otherwise.
     *
     * @param programmingParticipation The participation for which the repository should be accessed.
     * @param user                     The user who wants to access the repository.
     * @param programmingExercise      The programming exercise of the participation with the submission policy set.
     */
    private void checkHasParticipationPermissionsOrCanAccessSubmissionForPlagiarismComparisonElseThrow(ProgrammingExerciseParticipation programmingParticipation, User user,
            ProgrammingExercise programmingExercise) {
        boolean hasPermissions = participationAuthCheckService.canAccessParticipation(programmingParticipation, user);
        var exerciseDueDate = programmingExercise.isExamExercise() ? programmingExercise.getExerciseGroup().getExam().getEndDate() : programmingExercise.getDueDate();
        boolean hasAccessToSubmission = plagiarismService.hasAccessToSubmission(programmingParticipation.getId(), user.getLogin(), exerciseDueDate);
        boolean hasPlagiarismComparison = plagiarismService.hasPlagiarismComparison(programmingParticipation.getId());

        if (!hasPermissions) {
            if (!(hasAccessToSubmission && hasPlagiarismComparison)) {
                throw new AccessForbiddenException();
            }
        }
        else if (!hasAccessToSubmission && hasPlagiarismComparison) {
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

}
