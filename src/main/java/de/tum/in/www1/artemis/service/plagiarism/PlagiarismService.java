package de.tum.in.www1.artemis.service.plagiarism;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.PlagiarismResultRepository;

@Service
public class PlagiarismService {

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final PlagiarismResultRepository plagiarismResultRepository;

    public PlagiarismService(PlagiarismComparisonRepository plagiarismComparisonRepository, PlagiarismResultRepository plagiarismResultRepository) {
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    /**
     * Return an Optional of the latest PlagiarismResult for the given Exercise or empty, if no
     * plagiarism was detected yet.
     *
     * @param exercise Exercise to get the latest plagiarism result for.
     * @return the latest plagiarism result for the given exercise.
     */
    public Optional<PlagiarismResult> getPlagiarismResult(Exercise exercise) {
        return plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDesc(exercise.getId());
    }

    /**
     * Return the plagiarism comparison with the given ID or empty otherwise.
     * @param comparisonId ID of the plagiarism comparison to fetch.
     * @return the plagiarism comparison with the given ID.
     */
    public Optional<PlagiarismComparison> getPlagiarismComparison(long comparisonId) {
        return plagiarismComparisonRepository.findById(comparisonId);
    }

    /**
     * Update the status of the given plagiarism comparison.
     *
     * @param comparison Plagiarism comparison to update.
     * @param status The new status of the plagiarism comparison.
     */
    public void updateStatusOfComparison(PlagiarismComparison comparison, PlagiarismStatus status) {
        comparison.setStatus(status);
        plagiarismComparisonRepository.save(comparison);
    }

    /**
     * Store the given TextPlagiarismResult in the database.
     *
     * @param result TextPlagiarismResult to store in the database.
     */
    public void savePlagiarismResult(TextPlagiarismResult result) {
        plagiarismResultRepository.save(result);
    }

    /**
     * Store the given ModelingPlagiarismResult in the database.
     *
     * @param result ModelingPlagiarismResult to store in the database.
     */
    public void savePlagiarismResult(ModelingPlagiarismResult result) {
        plagiarismResultRepository.save(result);
    }

}
