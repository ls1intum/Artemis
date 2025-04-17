package de.tum.cit.aet.artemis.plagiarism.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;

@Conditional(PlagiarismEnabled.class)
@Controller
public class PlagiarismResultApi extends AbstractPlagiarismApi {

    private final PlagiarismResultRepository plagiarismResultRepository;

    public PlagiarismResultApi(PlagiarismResultRepository plagiarismResultRepository) {
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    public void deletePlagiarismResultsByExerciseId(Long exerciseId) {
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);
    }

    public PlagiarismResult<?> findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(long exerciseId) {
        return plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(exerciseId);
    }

    public void prepareResultForClient(PlagiarismResult<?> plagiarismResult) {
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }
}
