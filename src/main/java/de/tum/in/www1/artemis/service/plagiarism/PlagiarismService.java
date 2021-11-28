package de.tum.in.www1.artemis.service.plagiarism;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.PlagiarismResultRepository;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;

@Service
public class PlagiarismService {

    private final ExerciseRepository exerciseRepository;

    private final PlagiarismResultRepository plagiarismResultRepository;

    public PlagiarismService(ExerciseRepository exerciseRepository, PlagiarismResultRepository plagiarismResultRepository) {
        this.exerciseRepository = exerciseRepository;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    /**
     * Collects all plagiarism cases for a given course
     *
     * @param courseId of the course
     * @return the collected plagiarism cases
     */
    public ArrayList<PlagiarismCaseDTO> collectAllPlagiarismCasesForCourse(Long courseId) {
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
}
