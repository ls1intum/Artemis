package de.tum.in.www1.artemis.service.plagiarism;

import java.util.Optional;

import javax.transaction.Transactional;

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
    public Optional<PlagiarismResult<?>> getPlagiarismResult(Exercise exercise) {
        return plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDesc(exercise.getId());
    }

    /**
     * Delete the given plagiarism result.
     * @param result the result to delete.
     */
    public void deletePlagiarismResult(PlagiarismResult<?> result) {
        plagiarismResultRepository.delete(result);
    }

    /**
     * Update the status of the given plagiarism comparison.
     *
     * @param comparison Plagiarism comparison to update.
     * @param status The new status of the plagiarism comparison.
     */
    @Transactional // ok because of modifying query
    public void updateStatusOfComparison(PlagiarismComparison<?> comparison, PlagiarismStatus status) {
        plagiarismComparisonRepository.updatePlagiarismComparisonStatus(comparison.getId(), status);
    }

    /**
     * Store the given TextPlagiarismResult in the database.
     *
     * @param result TextPlagiarismResult to store in the database.
     */
    public void savePlagiarismResultAndRemovePrevious(TextPlagiarismResult result) {
        Optional<PlagiarismResult<?>> optionalPreviousResult = this.getPlagiarismResult(result.getExercise());
        plagiarismResultRepository.save(result);
        optionalPreviousResult.ifPresent(this::deletePlagiarismResult);
    }

    /**
     * Store the given ModelingPlagiarismResult in the database.
     *
     * @param result ModelingPlagiarismResult to store in the database.
     */
    public void savePlagiarismResultAndRemovePrevious(ModelingPlagiarismResult result) {
        Optional<PlagiarismResult<?>> optionalPreviousResult = this.getPlagiarismResult(result.getExercise());
        plagiarismResultRepository.save(result);
        optionalPreviousResult.ifPresent(this::deletePlagiarismResult);
    }
}
