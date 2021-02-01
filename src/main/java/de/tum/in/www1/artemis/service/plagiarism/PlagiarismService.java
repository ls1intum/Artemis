package de.tum.in.www1.artemis.service.plagiarism;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.PlagiarismResultRepository;

@Service
public class PlagiarismService {

    private final Logger logger = LoggerFactory.getLogger(PlagiarismService.class);

    private final PlagiarismResultRepository plagiarismResultRepository;

    public PlagiarismService(PlagiarismResultRepository plagiarismResultRepository) {
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    /**
     * Return an Optional of the latest TextPlagiarismResult for the given ProgrammingExercise or
     * empty, if no plagiarism was detected yet.
     *
     * @param exercise ProgrammingExercise to get the latest plagiarism result for.
     */
    public Optional<TextPlagiarismResult> getPlagiarismResult(ProgrammingExercise exercise) {
        return Optional.empty();
    }

    /**
     * Return an Optional of the latest TextPlagiarismResult for the given TextExercise or
     * empty, if no plagiarism was detected yet.
     *
     * @param exercise TextExercise to get the latest plagiarism result for.
     */
    public Optional<TextPlagiarismResult> getPlagiarismResult(TextExercise exercise) {
        return Optional.empty();
    }

    /**
     * Return an Optional of the latest ModelingPlagiarismResult for the given ModelingExercise or
     * empty, if no plagiarism was detected yet.
     *
     * @param exercise ModelingExercise to get the latest plagiarism result for.
     */
    public Optional<ModelingPlagiarismResult> getPlagiarismResult(ModelingExercise exercise) {
        return Optional.empty();
    }

    /**
     * Store the given TextPlagiarismResult in the database.
     *
     * @param result TextPlagiarismResult to store in the database.
     */
    public void savePlagiarismResult(TextPlagiarismResult result) {
        // TODO: Use `plagiarismResultRepository` to save the given result.
        plagiarismResultRepository.save(result);
    }

    /**
     * Store the given ModelingPlagiarismResult in the database.
     *
     * @param result ModelingPlagiarismResult to store in the database.
     */
    public void savePlagiarismResult(ModelingPlagiarismResult result) {
        // TODO: Use `plagiarismResultRepository` to save the given result.
        plagiarismResultRepository.save(result);
    }

}
