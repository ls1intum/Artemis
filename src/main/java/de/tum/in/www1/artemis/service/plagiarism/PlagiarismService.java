package de.tum.in.www1.artemis.service.plagiarism;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.PlagiarismResultRepository;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class PlagiarismService {

    // correspond to the translation files (suffix) used in the client
    private static final String YOUR_SUBMISSION = "Your submission";

    private static final String OTHER_SUBMISSION = "Other submission";

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismService(ExerciseRepository exerciseRepository, PlagiarismResultRepository plagiarismResultRepository,
            PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.exerciseRepository = exerciseRepository;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    /**
     * Collects all plagiarism cases for a given course
     *
     * @param courseId of the course
     * @return the collected plagiarism cases
     */
    public List<PlagiarismCaseDTO> collectAllPlagiarismCasesForCourse(Long courseId) {
        // TODO why do we do this so strangely (this is working legacy code)? Refactor in a follow up
        var collectedPlagiarismCases = new ArrayList<PlagiarismCaseDTO>();
        var exerciseIDs = exerciseRepository.findAllIdsByCourseId(courseId);
        exerciseIDs.forEach(id -> {
            var exerciseOptional = exerciseRepository.findById(id);
            if (exerciseOptional.isPresent()) {
                PlagiarismResult<?> result = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(exerciseOptional.get().getId());
                if (result != null) {
                    Set<PlagiarismComparison<?>> filteredComparisons = result.getComparisons().stream().filter(c -> c.getStatus() == PlagiarismStatus.CONFIRMED)
                            .collect(Collectors.toSet());
                    if (filteredComparisons.size() > 0) {
                        collectedPlagiarismCases.add(new PlagiarismCaseDTO(exerciseOptional.get(), filteredComparisons));
                    }
                }
            }
        });
        return collectedPlagiarismCases;
    }

    /**
     * Anonymizes the comparison for the student view.
     * A student should not have sensitive information (e.g. the userLogin of the other student)
     *
     * @param comparison that has to be anonymized.
     * @param userLogin of the student asking to see his plagiarism comparison.
     * @return the anoymized plagiarism comparison for the given student
     */
    public PlagiarismComparison anonymizeComparisonForStudentView(PlagiarismComparison comparison, String userLogin) {
        if (comparison.getSubmissionA().getStudentLogin().equals(userLogin)) {
            comparison.getSubmissionA().setStudentLogin(YOUR_SUBMISSION);
            comparison.getSubmissionB().setStudentLogin(OTHER_SUBMISSION);
            comparison.setInstructorStatementB(null);
        }
        else if (comparison.getSubmissionB().getStudentLogin().equals(userLogin)) {
            comparison.getSubmissionA().setStudentLogin(OTHER_SUBMISSION);
            comparison.getSubmissionB().setStudentLogin(YOUR_SUBMISSION);
            comparison.setInstructorStatementA(null);
        }
        else {
            throw new AccessForbiddenException("This plagiarism comparison is not related to the requesting user.");
        }
        return comparison;
    }

    /**
     * Anonymizes the submission for the student view.
     * A student should not see sensitive information but be able to retrieve both answers from both students for the comparison
     *
     * @param submission that has to be anonymized.
     * @param userLogin of the student asking to see his plagiarism comparison.
     * @return the anoymized submission for the given student
     */
    public Submission anonymizeSubmissionForStudentView(Submission submission, String userLogin) {
        var comparisonOptional = plagiarismComparisonRepository.findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(submission.getId(), submission.getId());

        // disallow requests from users who are not notified about this case:
        boolean isUserNotifiedByInstructor = false;
        if (comparisonOptional.isPresent()) {
            var comparisons = comparisonOptional.get();
            isUserNotifiedByInstructor = comparisons.stream()
                    .anyMatch(comparison -> (comparison.getInstructorStatementA() != null && (comparison.getSubmissionA().getStudentLogin().equals(userLogin)))
                            || (comparison.getInstructorStatementB() != null && (comparison.getSubmissionB().getStudentLogin().equals(userLogin))));
        }
        if (!isUserNotifiedByInstructor) {
            throw new AccessForbiddenException("This plagiarism submission is not related to the requesting user or the user has not been notified yet.");
        }
        submission.setParticipation(null);
        submission.setResults(null);
        submission.setSubmissionDate(null);
        return submission;
    }
}
