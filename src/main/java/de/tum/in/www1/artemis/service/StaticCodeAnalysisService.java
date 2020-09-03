package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;

@Service
public class StaticCodeAnalysisService {

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    public StaticCodeAnalysisService(StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository) {
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    /**
     * Returns all static code analysis categories of the given programming exercise.
     *
     * @param exerciseId of a programming exercise.
     * @return static code analysis categories of a programming exercise.
     */
    public Set<StaticCodeAnalysisCategory> findByExerciseId(Long exerciseId) {
        return this.staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseId);
    }
}
