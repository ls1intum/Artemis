package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hazelcast.internal.metrics.managementcenter.ConcurrentArrayRingbuffer;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;

@Service
public class StaticCodeAnalysisService {

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisService.class);

    @Qualifier("staticCodeAnalysisConfiguration")
    private final Map<ProgrammingLanguage, StaticCodeAnalysisConfiguration> staticCodeAnalysisDefaultConfigurations;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    public StaticCodeAnalysisService(StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository,
            Map<ProgrammingLanguage, StaticCodeAnalysisConfiguration> staticCodeAnalysisDefaultConfigurations) {
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.staticCodeAnalysisDefaultConfigurations = staticCodeAnalysisDefaultConfigurations;
    }

    /**
     * Returns all static code analysis categories of the given programming exercise.
     *
     * @param exerciseId of a programming exercise.
     * @return static code analysis categories of a programming exercise.
     */
    public Set<StaticCodeAnalysisCategory> findByExerciseId(Long exerciseId) {
        return staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseId);
    }

    /**
     * Creates static code analysis categories for a programming exercise using @{staticCodeAnalysisDefaultConfigurations}
     * as a template.
     *
     * @param programmingExercise for which the static code analysis categories will be created
     */
    public void createDefaultCategories(ProgrammingExercise programmingExercise) {
        // Retrieve the default configuration for a specific programming language
        StaticCodeAnalysisConfiguration defaultConfiguration = staticCodeAnalysisDefaultConfigurations.get(programmingExercise.getProgrammingLanguage());
        if (defaultConfiguration == null) {
            log.debug("Could not create default static code analysis categories for exercise " + programmingExercise.getId() + ". Default configuration not available.");
            return;
        }

        // Create new static code analysis using the default configuration as a template
        List<StaticCodeAnalysisCategory> newCategories = new ArrayList<>();
        for (var defaultCategory : defaultConfiguration.getDefaultCategories()) {
            StaticCodeAnalysisCategory newCategory = new StaticCodeAnalysisCategory();
            newCategory.setName(defaultCategory.getName());
            newCategory.setPenalty(defaultCategory.getPenalty());
            newCategory.setMaxPenalty(defaultCategory.getMaxPenalty());
            newCategory.setState(defaultCategory.getState());
            newCategory.setProgrammingExercise(programmingExercise);
            newCategories.add(newCategory);
        }
        staticCodeAnalysisCategoryRepository.saveAll(newCategories);
    }

    /**
     * Updates the static code analysis categories of a programming exercise.
     *
     * @param exerciseId of a programming exercise
     * @param updatedCategories updates for categories
     * @return updated categories
     */
    public Set<StaticCodeAnalysisCategory> updateCategories(Long exerciseId, Collection<StaticCodeAnalysisCategory> updatedCategories) {
        Set<StaticCodeAnalysisCategory> originalCategories = findByExerciseId(exerciseId);
        for (StaticCodeAnalysisCategory originalCategory : originalCategories) {
            // Find an updated category with the same id
            Optional<StaticCodeAnalysisCategory> matchingCategoryOptional = updatedCategories.stream()
                    .filter(updatedCategory -> originalCategory.getId().equals(updatedCategory.getId())).findFirst();

            // If no match is found, the original category won't be updated
            if (matchingCategoryOptional.isEmpty()) {
                continue;
            }
            StaticCodeAnalysisCategory matchingCategory = matchingCategoryOptional.get();

            // Update the original category
            originalCategory.setPenalty(matchingCategory.getPenalty());
            originalCategory.setMaxPenalty(matchingCategory.getMaxPenalty());
            originalCategory.setState(matchingCategory.getState());
        }
        staticCodeAnalysisCategoryRepository.saveAll(originalCategories);
        return originalCategories;
    }

    public List<ImmutablePair<StaticCodeAnalysisCategory, List<StaticCodeAnalysisConfiguration.CategoryMapping>>> getCategoriesWithMappingForExercise(
            ProgrammingExercise programmingExercise) {
        var categories = findByExerciseId(programmingExercise.getId());
        var defaultCategories = staticCodeAnalysisDefaultConfigurations.get(programmingExercise.getProgrammingLanguage()).getDefaultCategories();

        return categories.stream()
                .map(category -> defaultCategories.stream().filter(defaultCategory -> defaultCategory.getName().equals(category.getName())).findFirst()
                        .map(StaticCodeAnalysisConfiguration.DefaultCategory::getCategoryMappings).map(mappings -> new ImmutablePair<>(category, mappings)))
                .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
    }
}
