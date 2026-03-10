package de.tum.cit.aet.artemis.plagiarism.service;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;

@Service
@Conditional(PlagiarismEnabled.class)
@Lazy
public class PlagiarismAccessService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final ExerciseDateService exerciseDateService;

    public PlagiarismAccessService(PlagiarismComparisonRepository plagiarismComparisonRepository, ExerciseDateService exerciseDateService) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * A student should not see both answers from both students for the comparison before the due date
     *
     * @param submissionId  the id of the submission to check.
     * @param userLogin     the user login of the student asking to see their plagiarism comparison.
     * @param participation the participation of the student asking to see their plagiarism comparison.
     * @return true is the user has access to the submission
     */
    public boolean hasAccessToSubmission(Long submissionId, String userLogin, Participation participation) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submissionId, submissionId);
        return comparisonOptional.filter(not(Set::isEmpty)).isPresent() && isOwnSubmissionOrIsAfterExerciseDueDate(submissionId, userLogin, comparisonOptional.get(), participation)
                && wasUserNotifiedByInstructor(userLogin, comparisonOptional.get());
    }

    private boolean isOwnSubmissionOrIsAfterExerciseDueDate(Long submissionId, String userLogin, Set<PlagiarismComparison> comparisons, Participation participation) {
        var isOwnSubmission = comparisons.stream().flatMap(it -> Stream.of(it.getSubmissionA(), it.getSubmissionB())).filter(Objects::nonNull)
                .filter(it -> it.getSubmissionId() == submissionId).findFirst().map(PlagiarismSubmission::getStudentLogin).filter(isEqual(userLogin)).isPresent();
        return isOwnSubmission || exerciseDateService.isAfterDueDate(participation);
    }

    /**
     * Checks whether the student with the given user login is involved in a plagiarism case which contains the given submissionId and the student is notified by the instructor.
     *
     * @param submission the submission to check
     * @param userLogin  the user login of the student
     * @return true if the student with user login owns one of the submissions in a PlagiarismComparison which contains the given submissionId and is notified by the instructor,
     *         otherwise false
     */
    public boolean wasUserNotifiedByInstructor(Submission submission, String userLogin) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submission.getId(), submission.getId());
        return comparisonOptional.filter(not(Set::isEmpty)).isPresent() && wasUserNotifiedByInstructor(userLogin, comparisonOptional.get());
    }

    private boolean wasUserNotifiedByInstructor(String userLogin, Set<PlagiarismComparison> comparisons) {
        // disallow requests from users who are not notified about this case:
        return comparisons.stream()
                .anyMatch(comparison -> (comparison.getSubmissionA().getPlagiarismCase() != null
                        && (comparison.getSubmissionA().getPlagiarismCase().getPost() != null || comparison.getSubmissionA().getPlagiarismCase().getVerdict() != null)
                        && (comparison.getSubmissionA().getStudentLogin().equals(userLogin)))
                        || (comparison.getSubmissionB().getPlagiarismCase() != null
                                && (comparison.getSubmissionB().getPlagiarismCase().getPost() != null || comparison.getSubmissionB().getPlagiarismCase().getVerdict() != null)
                                && (comparison.getSubmissionB().getStudentLogin().equals(userLogin))));
    }

}
