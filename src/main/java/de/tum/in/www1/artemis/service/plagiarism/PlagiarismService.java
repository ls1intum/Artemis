package de.tum.in.www1.artemis.service.plagiarism;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
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
     * Return an Optional of the latest PlagiarismResult for the given Exercise or empty, if no
     * plagiarism was detected yet.
     *
     * @param exercise Exercise to get the latest plagiarism result for.
     */
    public Optional<PlagiarismResult> getPlagiarismResult(Exercise exercise) {
        return plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDesc(exercise.getId());
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
