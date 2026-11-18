package de.tum.cit.aet.artemis.plagiarism.service;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismSubmissionRepository;

@Service
@Conditional(PlagiarismEnabled.class)
@Lazy
public class PlagiarismAccessService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismSubmissionRepository plagiarismSubmissionRepository;

    private final ExerciseDateService exerciseDateService;

    public PlagiarismAccessService(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismSubmissionRepository plagiarismSubmissionRepository,
            ExerciseDateService exerciseDateService) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismSubmissionRepository = plagiarismSubmissionRepository;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Finds the commit that was compared for a participation of a programming exercise.
     *
     * @param exerciseId      the id of the programming exercise
     * @param participationId the id of the participation, which is the compared id of a programming comparison
     * @return the hash of the most recently compared commit, empty if no hash was stored (e.g. for results created before hashes were stored)
     */
    public Optional<String> findComparedCommitHash(long exerciseId, long participationId) {
        return plagiarismSubmissionRepository.findComparedCommitHashes(exerciseId, participationId, Pageable.ofSize(1)).stream().findFirst();
    }

    /**
     * Finds when the most recent plagiarism check that compared a participation of a programming exercise was created.
     *
     * @param exerciseId      the id of the programming exercise
     * @param participationId the id of the participation, which is the compared id of a programming comparison
     * @return the creation date of the most recent result, empty if the participation was not compared
     */
    public Optional<Instant> findComparisonCreatedDate(long exerciseId, long participationId) {
        return plagiarismSubmissionRepository.findComparisonCreatedDates(exerciseId, participationId, Pageable.ofSize(1)).stream().findFirst();
    }

    /**
     * A student should not see both answers from both students for the comparison before the due date
     *
     * @param exerciseId    the id of the exercise the submission belongs to, only comparisons of this exercise are considered.
     * @param submissionId  the id of the submission to check.
     * @param userLogin     the user login of the student asking to see their plagiarism comparison.
     * @param participation the participation of the student asking to see their plagiarism comparison.
     * @return true is the user has access to the submission
     */
    public boolean hasAccessToSubmission(Long exerciseId, Long submissionId, String userLogin, Participation participation) {
        var comparisonOptional = plagiarismComparisonRepository.findByPlagiarismResultExerciseIdAndSubmissions_SubmissionId(exerciseId, submissionId);
        return comparisonOptional.filter(not(Set::isEmpty)).isPresent() && isOwnSubmissionOrIsAfterExerciseDueDate(submissionId, userLogin, comparisonOptional.get(), participation)
                && wasUserNotifiedByInstructor(userLogin, comparisonOptional.get());
    }

    private boolean isOwnSubmissionOrIsAfterExerciseDueDate(Long submissionId, String userLogin, Set<PlagiarismComparison> comparisons, Participation participation) {
        var isOwnSubmission = comparisons.stream().flatMap(it -> it.getSubmissions().stream()).filter(Objects::nonNull).filter(it -> it.getSubmissionId() == submissionId)
                .findFirst().map(PlagiarismSubmission::getStudentLogin).filter(isEqual(userLogin)).isPresent();
        return isOwnSubmission || exerciseDateService.isAfterDueDate(participation);
    }

    /**
     * Checks whether the student with the given user login is involved in a plagiarism case which contains the given submissionId and the student is notified by the instructor.
     *
     * @param exerciseId the id of the exercise the submission belongs to, only comparisons of this exercise are considered
     * @param submission the submission to check
     * @param userLogin  the user login of the student
     * @return true if the student with user login owns one of the submissions in a PlagiarismComparison of the exercise which contains the given submissionId and is notified by
     *         the instructor, otherwise false
     */
    public boolean wasUserNotifiedByInstructor(Long exerciseId, Submission submission, String userLogin) {
        var comparisonOptional = plagiarismComparisonRepository.findByPlagiarismResultExerciseIdAndSubmissions_SubmissionId(exerciseId, submission.getId());
        return comparisonOptional.filter(not(Set::isEmpty)).isPresent() && wasUserNotifiedByInstructor(userLogin, comparisonOptional.get());
    }

    private boolean wasUserNotifiedByInstructor(String userLogin, Set<PlagiarismComparison> comparisons) {
        // disallow requests from users who are not notified about this case:
        return comparisons.stream().flatMap(comparison -> comparison.getSubmissions().stream()).anyMatch(submission -> submission.getPlagiarismCase() != null
                && (submission.getPlagiarismCase().getPost() != null || submission.getPlagiarismCase().getVerdict() != null) && userLogin.equals(submission.getStudentLogin()));
    }

}
