package de.tum.in.www1.artemis.service.plagiarism;

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
     * Return the latest TextPlagiarismResult for the given ProgrammingExercise or null,
     * if no plagiarism was detected yet.
     *
     * @param exercise ProgrammingExercise to get the latest plagiarism result for.
     */
    public TextPlagiarismResult getPlagiarismResult(ProgrammingExercise exercise) {
        return null;
    }

    /**
     * Return the latest TextPlagiarismResult for the given TextExercise or null,
     * if no plagiarism was detected yet.
     *
     * @param exercise TextExercise to get the latest plagiarism result for.
     */
    public TextPlagiarismResult getPlagiarismResult(TextExercise exercise) {
        return null;
    }

    /**
     * Return the latest TextPlagiarismResult for the given ModelingExercise or null,
     * if no plagiarism was detected yet.
     *
     * @param exercise ModelingExercise to get the latest plagiarism result for.
     */
    public ModelingPlagiarismResult getPlagiarismResult(ModelingExercise exercise) {
        return null;
    }

}
