package de.tum.cit.aet.artemis.plagiarism.api;

import java.util.Collection;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;

@Conditional(PlagiarismEnabled.class)
@Controller
@Lazy
public class PlagiarismResultApi extends AbstractPlagiarismApi {

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    public PlagiarismResultApi(PlagiarismResultRepository plagiarismResultRepository, PlagiarismComparisonRepository plagiarismComparisonRepository) {
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
    }

    public void deletePlagiarismResultsByExerciseId(Long exerciseId) {
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);
    }

    public long countComparisonsByExerciseIds(Collection<Long> exerciseIds) {
        return exerciseIds.isEmpty() ? 0 : plagiarismComparisonRepository.countByExerciseIds(exerciseIds);
    }

    public PlagiarismResult findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(long exerciseId) {
        return plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(exerciseId);
    }

    public void prepareResultForClient(PlagiarismResult plagiarismResult) {
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }
}
