package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.AccessUnauthorizedException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

/**
 * Service for checking if a user has access to a repository.
 */
@Service
public class RepositoryAccessService {

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final PlagiarismService plagiarismService;

    private final SubmissionPolicyService submissionPolicyService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExamSubmissionService examSubmissionService;

    public RepositoryAccessService(ProgrammingExerciseParticipationService programmingExerciseParticipationService, PlagiarismService plagiarismService,
            SubmissionPolicyService submissionPolicyService, AuthorizationCheckService authorizationCheckService, ExamSubmissionService examSubmissionService) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.plagiarismService = plagiarismService;
        this.submissionPolicyService = submissionPolicyService;
        this.authorizationCheckService = authorizationCheckService;
        this.examSubmissionService = examSubmissionService;
    }

    /**
     * Checks if the user has access to the repository of the given participation.
     *
     * @param programmingParticipation The participation for which the repository should be accessed.
     * @param user                     The user who wants to access the repository.
     * @param programmingExercise      The programming exercise of the participation.
     * @param repositoryActionType     The type of action that the user wants to perform on the repository (i.e. WRITE or READ).
     */
    public void checkAccessRepositoryElseThrow(ProgrammingExerciseParticipation programmingParticipation, User user, ProgrammingExercise programmingExercise,
            RepositoryActionType repositoryActionType) {

        // Error case 1: The user does not have permissions to push into the repository and the user is not notified for a related plagiarism case.
        boolean hasPermissions = programmingExerciseParticipationService.canAccessParticipation(programmingParticipation);
        boolean userWasNotifiedAboutPlagiarismCase = plagiarismService.wasUserNotifiedByInstructor(programmingParticipation.getId(), user.getLogin());
        if (!hasPermissions && !userWasNotifiedAboutPlagiarismCase) {
            throw new AccessUnauthorizedException();
        }

        // Error case 2: The user's participation repository is locked.
        boolean lockRepositoryPolicyEnforced = false;
        if (programmingExercise.getSubmissionPolicy() instanceof LockRepositoryPolicy policy) {
            lockRepositoryPolicyEnforced = submissionPolicyService.isParticipationLocked(policy, (Participation) programmingParticipation);
        }
        if (repositoryActionType == RepositoryActionType.WRITE && (programmingParticipation.isLocked() || lockRepositoryPolicyEnforced)) {
            throw new AccessForbiddenException();
        }

        boolean isStudent = !authorizationCheckService.isAtLeastTeachingAssistantInCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), user);

        // Error case 3: The student can reset the repository only before and a tutor/instructor only after the due date has passed
        if (repositoryActionType == RepositoryActionType.RESET) {
            boolean isOwner = true; // true for Solution- and TemplateProgrammingExerciseParticipation
            if (programmingParticipation instanceof StudentParticipation) {
                isOwner = authorizationCheckService.isOwnerOfParticipation((StudentParticipation) programmingParticipation);
            }
            if (isStudent && programmingParticipation.isLocked()) {
                throw new AccessForbiddenException();
            }
            // A tutor/instructor who is owner of the exercise should always be able to reset the repository
            else if (!isStudent && !isOwner) {
                // Check if a tutor is allowed to reset during the assessment
                // Check for a regular course exercise
                if (programmingExercise.isCourseExercise() && !programmingParticipation.isLocked()) {
                    throw new AccessForbiddenException();
                }
                // Check for an exam exercise, as it might not be locked but a student might still be allowed to submit
                var optStudent = ((StudentParticipation) programmingParticipation).getStudent();
                if (optStudent.isPresent() && programmingExercise.isExamExercise()
                        && examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, optStudent.get(), false)) {
                    throw new AccessForbiddenException();
                }
            }
        }

        // Error case 4: The user is not (any longer) allowed to submit to the exam/exercise. This check is only relevant for students.
        // This must be a student participation as hasPermissions would have been false and an error already thrown
        // But the student should still be able to access if they are notified for a related plagiarism case.
        boolean isStudentParticipation = programmingParticipation instanceof ProgrammingExerciseStudentParticipation;
        if (isStudentParticipation && isStudent && !examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, user, false) && !userWasNotifiedAboutPlagiarismCase) {
            throw new AccessForbiddenException();
        }
    }

    /**
     * Checks if the user has access to the test repository of the given programming exercise.
     *
     * @param atLeastEditor if true, the user needs at least editor permissions, otherwise only teaching assistant permissions are required.
     * @param exercise      the programming exercise the test repository belongs to.
     * @param user          the user that wants to access the test repository.
     */
    public void checkAccessTestRepositoryElseThrow(boolean atLeastEditor, ProgrammingExercise exercise, User user) {
        // The only test-repository endpoints that require at least editor permissions are the getStatus (GET /api/test-repository/{exerciseId}) and updateTestFiles (PUT
        // /api/test-repository/{exerciseId}/files) endpoints.
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
