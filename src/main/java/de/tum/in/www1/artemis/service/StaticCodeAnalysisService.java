package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;

@Service
public class StaticCodeAnalysisService {

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisService.class);

    @Qualifier("staticCodeAnalysisConfiguration")
    private final Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisDefaultConfigurations;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    public StaticCodeAnalysisService(StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository,
            Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisDefaultConfigurations, ProgrammingTriggerService programmingTriggerService) {
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.staticCodeAnalysisDefaultConfigurations = staticCodeAnalysisDefaultConfigurations;
        this.programmingTriggerService = programmingTriggerService;
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
        List<StaticCodeAnalysisDefaultCategory> defaultConfiguration = staticCodeAnalysisDefaultConfigurations.get(programmingExercise.getProgrammingLanguage());
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
        Set<StaticCodeAnalysisCategory> categories = findByExerciseId(exercise.getId());
        List<StaticCodeAnalysisDefaultCategory> defaultCategories = staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage());
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
     * Links the categories of an exercise with the default category mappings.
     * @param programmingExercise The programming exercise
     * @return A list of pairs of categories and their mappings.
     */
    public List<ImmutablePair<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>>> getCategoriesWithMappingForExercise(
            ProgrammingExercise programmingExercise) {
        var categories = findByExerciseId(programmingExercise.getId());
        var defaultCategories = staticCodeAnalysisDefaultConfigurations.get(programmingExercise.getProgrammingLanguage());

        List<ImmutablePair<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>>> categoryPairsWithMapping = new ArrayList<>();

        for (var category : categories) {
            var defaultCategoryMatch = defaultCategories.stream().filter(defaultCategory -> defaultCategory.getName().equals(category.getName())).findFirst();
            if (defaultCategoryMatch.isPresent()) {
                var categoryMappings = defaultCategoryMatch.get().getCategoryMappings();
                categoryPairsWithMapping.add(new ImmutablePair<>(category, categoryMappings));
            }
        }

        return categoryPairsWithMapping;
    }

    /**
     * Sets the category for each feedback and removes feedback with no category or an inactive one.
     * The feedback is removed permanently, which has the advantage that the server or client doesn't have to filter out
     * invisible feedback every time it is requested. The drawback is that the re-evaluate functionality can't take
     * the removed feedback into account.
     *
     * @param result of the build run
     * @param staticCodeAnalysisFeedback List of static code analysis feedback objects
     * @param programmingExercise The current exercise
     * @return The filtered list of feedback objects
     */
    public List<Feedback> categorizeScaFeedback(Result result, List<Feedback> staticCodeAnalysisFeedback, ProgrammingExercise programmingExercise) {
        var categoryPairs = getCategoriesWithMappingForExercise(programmingExercise);

        return staticCodeAnalysisFeedback.stream().filter(feedback -> {
            // ObjectMapper to extract the static code analysis issue from the feedback
            ObjectMapper mapper = new ObjectMapper();
            // the category for this feedback
            Optional<StaticCodeAnalysisCategory> category = Optional.empty();
            try {
                // extract the sca issue
                var issue = mapper.readValue(feedback.getDetailText(), StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue.class);

                // find the category for this issue
                for (var categoryPair : categoryPairs) {
                    var categoryMappings = categoryPair.right;
                    if (categoryMappings.stream()
                            .anyMatch(mapping -> mapping.getTool().name().equals(feedback.getReference()) && mapping.getCategory().equals(issue.getCategory()))) {
                        category = Optional.of(categoryPair.left);
                        break;
                    }
                }

                if (category.isPresent()) {
                    if (category.get().getState() == CategoryState.GRADED) {
                        // update the penalty of the issue
                        issue.setPenalty(category.get().getPenalty());
                    }
                    else if (issue.getPenalty() != null) {
                        // remove the penalty of the issue
                        issue.setPenalty(null);
                    }
                    feedback.setDetailText(mapper.writeValueAsString(issue));
                }
            }
            catch (JsonProcessingException exception) {
                log.debug("Error occurred parsing feedback {} to static code analysis issue: {}", feedback, exception.getMessage());
            }

            if (category.isEmpty() || category.get().getState().equals(CategoryState.INACTIVE)) {
                // remove feedback in no category or an inactive one
                result.removeFeedback(feedback);
                return false; // filter this feedback
            }
            else {
                // add the category name to the feedback text
                feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + category.get().getName());
                return true; // keep this feedback
            }
        }).collect(Collectors.toCollection(ArrayList::new));
    }
}
