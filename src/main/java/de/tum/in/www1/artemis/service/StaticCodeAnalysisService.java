package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.StaticCodeAnalysisConfigurer;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;

@Service
public class StaticCodeAnalysisService {

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisService.class);

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    public StaticCodeAnalysisService(StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository, ProgrammingTriggerService programmingTriggerService) {
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.programmingTriggerService = programmingTriggerService;
    }

    /**
     * Creates static code analysis categories for a programming exercise using @{staticCodeAnalysisDefaultConfigurations}
     * as a template.
     *
     * @param programmingExercise for which the static code analysis categories will be created
     */
    public void createDefaultCategories(ProgrammingExercise programmingExercise) {
        // Retrieve the default configuration for a specific programming language
        List<StaticCodeAnalysisDefaultCategory> defaultConfiguration = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration()
                .get(programmingExercise.getProgrammingLanguage());
        if (defaultConfiguration == null) {
            log.debug("Could not create default static code analysis categories for exercise {}. Default configuration not available.", programmingExercise.getId());
            return;
        }

        // Create new static code analysis using the default configuration as a template
        List<StaticCodeAnalysisCategory> newCategories = new ArrayList<>();
        for (var defaultCategory : defaultConfiguration) {
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
     * @param exerciseId        of a programming exercise
     * @param updatedCategories updates for categories
     * @return updated categories
     */
    public Set<StaticCodeAnalysisCategory> updateCategories(Long exerciseId, Collection<StaticCodeAnalysisCategory> updatedCategories) {
        Set<StaticCodeAnalysisCategory> originalCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseId);
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

        // At least one category was updated. We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);

        return originalCategories;
    }

    /**
     * Restore the default configuration for static code analysis categories of the given exercise.
     * Categories without a default configuration are ignored.
     * Returns the original categories if a default configuration could not be found.
     *
     * @param exercise exercise for which the static code analysis category configuration should be restored
     * @return static code analysis categories with default configuration
     */
    public Set<StaticCodeAnalysisCategory> resetCategories(ProgrammingExercise exercise) {
        Set<StaticCodeAnalysisCategory> categories = staticCodeAnalysisCategoryRepository.findByExerciseId(exercise.getId());
        List<StaticCodeAnalysisDefaultCategory> defaultCategories = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(exercise.getProgrammingLanguage());
        if (defaultCategories == null) {
            log.debug("Could not reset static code analysis categories for exercise {}. Default configuration not available.", exercise.getId());
            return categories;
        }

        // Restore the default configuration. Ignore unknown categories by iterating over the default categories
        for (var defaultCategory : defaultCategories) {
            var matchingCategory = categories.stream().filter(category -> Objects.equals(defaultCategory.getName(), category.getName())).findFirst();
            matchingCategory.ifPresent(cat -> {
                cat.setPenalty(defaultCategory.getPenalty());
                cat.setMaxPenalty(defaultCategory.getMaxPenalty());
                cat.setState(defaultCategory.getState());
            });
        }
        staticCodeAnalysisCategoryRepository.saveAll(categories);

        // We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChangedAndTriggerTestCaseUpdate(exercise.getId());

        return categories;
    }

    /**
     * This method allows users to reuse an already existing SCA configuration by copying it into another exercise.
     * The previous configuration of the targeted exercise will get removed.
     *
     * @param sourceExercise The exercise to take the existing configuration from
     * @param targetExercise The exercise into which the configuration gets copied in
     * @return the new SCA configuration of the targetExercise
     */
    public Set<StaticCodeAnalysisCategory> importCategoriesFromExercise(ProgrammingExercise sourceExercise, ProgrammingExercise targetExercise) {
        var sourceCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(sourceExercise.getId());
        var oldCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(targetExercise.getId());

        var newCategories = sourceCategories.stream().map(category -> {
            var copy = category.copy();
            copy.setProgrammingExercise(targetExercise);
            return copy;
        }).collect(Collectors.toSet());

        staticCodeAnalysisCategoryRepository.deleteAll(oldCategories);
        staticCodeAnalysisCategoryRepository.saveAll(newCategories);

        // We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChangedAndTriggerTestCaseUpdate(targetExercise.getId());

        return newCategories;
    }
}
