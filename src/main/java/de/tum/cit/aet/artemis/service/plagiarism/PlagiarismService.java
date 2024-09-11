package de.tum.cit.aet.artemis.service.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ExerciseDateService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;

@Profile(PROFILE_CORE)
@Service
public class PlagiarismService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    private final AuthorizationCheckService authCheckService;

    private final SubmissionRepository submissionRepository;

    private final UserRepository userRepository;

    private final ExerciseDateService exerciseDateService;

    public PlagiarismService(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismCaseService plagiarismCaseService, AuthorizationCheckService authCheckService,
            SubmissionRepository submissionRepository, UserRepository userRepository, ExerciseDateService exerciseDateService) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismCaseService = plagiarismCaseService;
        this.authCheckService = authCheckService;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Anonymize the submission for the student view.
     * A student should not see sensitive information but be able to retrieve both answers from both students for the comparison
     *
     * @param submission    the submission to anonymize.
     * @param userLogin     the user login of the student asking to see his plagiarism comparison.
     * @param participation the participation of the student asking to see his plagiarism comparison.
     */
    public void checkAccessAndAnonymizeSubmissionForStudent(Submission submission, String userLogin, Participation participation) {
        if (!hasAccessToSubmission(submission.getId(), userLogin, participation)) {
            throw new AccessForbiddenException("This plagiarism submission is not related to the requesting user or the user has not been notified yet.");
        }
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
    }

    /**
     * A student should not see both answers from both students for the comparison before the due date
     *
     * @param submissionId  the id of the submission to check.
     * @param userLogin     the user login of the student asking to see his plagiarism comparison.
     * @param participation the participation of the student asking to see his plagiarism comparison.
     * @return true is the user has access to the submission
     */
    public boolean hasAccessToSubmission(Long submissionId, String userLogin, Participation participation) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submissionId, submissionId);
        return comparisonOptional.filter(not(Set::isEmpty)).isPresent() && isOwnSubmissionOrIsAfterExerciseDueDate(submissionId, userLogin, comparisonOptional.get(), participation)
                && wasUserNotifiedByInstructor(userLogin, comparisonOptional.get());
    }

    private boolean isOwnSubmissionOrIsAfterExerciseDueDate(Long submissionId, String userLogin, Set<PlagiarismComparison<?>> comparisons, Participation participation) {
        var isOwnSubmission = comparisons.stream().flatMap(it -> Stream.of(it.getSubmissionA(), it.getSubmissionB())).filter(Objects::nonNull)
                .filter(it -> it.getSubmissionId() == submissionId).findFirst().map(PlagiarismSubmission::getStudentLogin).filter(isEqual(userLogin)).isPresent();
        return isOwnSubmission || exerciseDateService.isAfterDueDate(participation);
    }

    /**
     * Checks whether the student with the given user login is involved in a plagiarism case which contains the given submissionId and the student is notified by the instructor.
     *
     * @param userLogin the user login of the student
     * @return true if the student with user login owns one of the submissions in a PlagiarismComparison which contains the given submissionId and is notified by the instructor,
     *         otherwise false
     */
    private boolean wasUserNotifiedByInstructor(String userLogin, Set<PlagiarismComparison<?>> comparisons) {
        // disallow requests from users who are not notified about this case:
        return comparisons.stream()
                .anyMatch(comparison -> (comparison.getSubmissionA().getPlagiarismCase() != null
                        && (comparison.getSubmissionA().getPlagiarismCase().getPost() != null || comparison.getSubmissionA().getPlagiarismCase().getVerdict() != null)
                        && (comparison.getSubmissionA().getStudentLogin().equals(userLogin)))
                        || (comparison.getSubmissionB().getPlagiarismCase() != null
                                && (comparison.getSubmissionB().getPlagiarismCase().getPost() != null || comparison.getSubmissionB().getPlagiarismCase().getVerdict() != null)
                                && (comparison.getSubmissionB().getStudentLogin().equals(userLogin))));
    }

    /**
     * Update the status of the plagiarism comparison.
     *
     * @param plagiarismComparisonId the ID of the plagiarism comparison
     * @param plagiarismStatus       the status to be set
     */
    public void updatePlagiarismComparisonStatus(long plagiarismComparisonId, PlagiarismStatus plagiarismStatus) {
        plagiarismComparisonRepository.updatePlagiarismComparisonStatus(plagiarismComparisonId, plagiarismStatus);
        if (plagiarismStatus.equals(PlagiarismStatus.CONFIRMED)) {
            plagiarismCaseService.createOrAddToPlagiarismCasesForComparison(plagiarismComparisonId);
        }
        else if (plagiarismStatus.equals(PlagiarismStatus.DENIED)) {
            plagiarismCaseService.removeSubmissionsInPlagiarismCasesForComparison(plagiarismComparisonId);
        }
    }

    /**
     * Retrieves the number of potential plagiarism cases by considering the plagiarism submissions for the exercise
     * Additionally, it filters out cases for deleted user --> isDeleted = true because we do not delete the user entity entirely.
     *
     * @param exerciseId the exercise id for which the potential plagiarism cases should be retrieved
     * @return the number of potential plagiarism cases
     */
    public long getNumberOfPotentialPlagiarismCasesForExercise(long exerciseId) {
        var comparisons = plagiarismComparisonRepository.findAllByPlagiarismResultExerciseId(exerciseId);
        Set<PlagiarismSubmission<?>> submissionsWithoutDeletedUsers = new HashSet<>();
        for (var comparison : comparisons) {
            addSubmissionsIfUserHasNotBeenDeleted(comparison, submissionsWithoutDeletedUsers);
        }
        return submissionsWithoutDeletedUsers.size();
    }

    /**
     * Add each submission of the plagiarism comparison if the corresponding user has not been deleted
     *
     * @param comparison                     the comparison for which we want check if the user of the submission has been deleted.
     * @param submissionsWithoutDeletedUsers a set of plagiarism submissions for which the user still exists.
     */
    private void addSubmissionsIfUserHasNotBeenDeleted(PlagiarismComparison<?> comparison, Set<PlagiarismSubmission<?>> submissionsWithoutDeletedUsers) {
        var plagiarismSubmissionA = comparison.getSubmissionA();
        var plagiarismSubmissionB = comparison.getSubmissionB();
        var submissionA = submissionRepository.findById(plagiarismSubmissionA.getSubmissionId()).orElseThrow();
        var submissionB = submissionRepository.findById(plagiarismSubmissionB.getSubmissionId()).orElseThrow();
        if (!userForSubmissionDeleted(submissionA)) {
            submissionsWithoutDeletedUsers.add(plagiarismSubmissionA);
        }
        if (!userForSubmissionDeleted(submissionB)) {
            submissionsWithoutDeletedUsers.add(plagiarismSubmissionB);

        }
    }

    /**
     * Checks if the user the submission belongs to, has not the isDeleted flag set to true
     *
     * @param submission the submission to check
     * @return true if the user is NOT deleted, false otherwise
     */
    private boolean userForSubmissionDeleted(Submission submission) {
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            var user = userRepository.findOneByLogin(studentParticipation.getParticipant().getParticipantIdentifier());
            if (user.isPresent()) {
                return user.get().isDeleted();
            }
        }
        return true; // if the user is not found, we assume that the user has been deleted
    }

    /**
     * filter a participation based on the last submission and make sure the last result - if available - has a minimum score - in case this is larger than 0
     *
     * @param minimumScore the minimum score
     * @return a predicate that can be used in streams for filtering
     */
    @NotNull
    public static Predicate<ProgrammingExerciseParticipation> filterParticipationMinimumScore(int minimumScore) {
        return participation -> {
            Submission submission = participation.findLatestSubmission().orElse(null);
            // filter empty submissions
            if (submission == null) {
                return false;
            }
            return hasMinimumScore(submission, minimumScore);
        };
    }

    /**
     * filter a submission so that the last result - if available - has a minimum score - in case this is larger than 0
     *
     * @param submission   the submission which latest result should be tested for filtering
     * @param minimumScore the minimum score
     * @return a predicate that can be used in streams for filtering
     */
    public static boolean hasMinimumScore(Submission submission, int minimumScore) {
        return minimumScore == 0
                || submission.getLatestResult() != null && submission.getLatestResult().getScore() != null && submission.getLatestResult().getScore() >= minimumScore;
    }

    /**
     * filter a student participation so that it includes a student that is not at least a tutor, in case of team participations no filter is applied
     *
     * @return a predicate that can be used in streams for filtering
     */
    @NotNull
    public Predicate<StudentParticipation> filterForStudents() {
        return participation -> {
            // this filter excludes individual participations that are not from students
            if (participation.getStudent().isPresent()) {
                return !authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), participation.getStudent().get());
            }
            // however, we make sure that team participations are included (in the unlikely case, neither student and team is defined, we filter)
            else {
                return participation.getTeam().isPresent();
            }
        };
    }
}
