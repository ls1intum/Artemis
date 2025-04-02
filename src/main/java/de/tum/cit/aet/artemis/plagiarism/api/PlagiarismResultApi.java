package de.tum.cit.aet.artemis.plagiarism.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;

@Profile(PROFILE_CORE)
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
