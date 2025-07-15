package de.tum.cit.aet.artemis.plagiarism.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;

@ConditionalOnProperty(name = "artemis.plagiarism.enabled", havingValue = "true")
@Controller
public class PlagiarismResultApi extends AbstractPlagiarismApi {

    private final PlagiarismResultRepository plagiarismResultRepository;

    public PlagiarismResultApi(PlagiarismResultRepository plagiarismResultRepository) {
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    public void deletePlagiarismResultsByExerciseId(Long exerciseId) {
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);
    }

    public PlagiarismResult findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(long exerciseId) {
        return plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(exerciseId);
    }

    public void prepareResultForClient(PlagiarismResult plagiarismResult) {
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }
}
