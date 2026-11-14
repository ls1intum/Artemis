package de.tum.cit.aet.artemis.plagiarism.service;

import static java.util.function.Predicate.not;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

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
     * A student should not see both answers from both students for the comparison before the due date.
     * The compared submissions are looked up through the submissions of the participation, as a plagiarism comparison refers to submissions and not to participations.
     *
     * @param participation the participation whose content the user wants to see.
     * @param userLogin     the user login of the student asking to see their plagiarism comparison.
     * @return true is the user has access to the participation
     */
    public boolean hasAccessToParticipation(Participation participation, String userLogin) {
        Set<PlagiarismComparison> comparisons = plagiarismComparisonRepository.findAllBySubmissionOfParticipation(participation.getId());
        if (comparisons.isEmpty()) {
            return false;
        }
        boolean isOwnSubmission = plagiarismComparisonRepository.findPlagiarismSubmissionsOfParticipation(participation.getId()).stream()
                .anyMatch(plagiarismSubmission -> userLogin.equals(plagiarismSubmission.getStudentLogin()));
        return (isOwnSubmission || exerciseDateService.isAfterDueDate(participation)) && wasUserNotifiedByInstructor(userLogin, comparisons);
    }

    /**
     * Finds the submission of the participation that was compared. If several were compared, the latest one is used.
     *
     * @param participationId the id of the participation
     * @return the id of the compared submission, or empty if no submission of the participation was compared
     */
    public Optional<Long> findComparedSubmissionId(long participationId) {
        return plagiarismComparisonRepository.findPlagiarismSubmissionsOfParticipation(participationId).stream().map(PlagiarismSubmission::getSubmissionId)
                .max(Comparator.naturalOrder());
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
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissions_SubmissionId(submission.getId());
        return comparisonOptional.filter(not(Set::isEmpty)).isPresent() && wasUserNotifiedByInstructor(userLogin, comparisonOptional.get());
    }

    private boolean wasUserNotifiedByInstructor(String userLogin, Set<PlagiarismComparison> comparisons) {
        // disallow requests from users who are not notified about this case:
        return comparisons.stream().flatMap(comparison -> comparison.getSubmissions().stream()).anyMatch(submission -> submission.getPlagiarismCase() != null
                && (submission.getPlagiarismCase().getPost() != null || submission.getPlagiarismCase().getVerdict() != null) && userLogin.equals(submission.getStudentLogin()));
    }

}
