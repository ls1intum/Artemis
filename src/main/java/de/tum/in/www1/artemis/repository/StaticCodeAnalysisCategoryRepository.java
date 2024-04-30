package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.StaticCodeAnalysisConfigurer;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisDefaultCategory;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

/**
 * Spring Data repository for the StaticCodeAnalysisCategory entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface StaticCodeAnalysisCategoryRepository extends JpaRepository<StaticCodeAnalysisCategory, Long> {

    Logger log = LoggerFactory.getLogger(StaticCodeAnalysisCategoryRepository.class);

    Set<StaticCodeAnalysisCategory> findByExerciseId(long exerciseId);

    @Query("""
            SELECT s
            FROM StaticCodeAnalysisCategory s
                LEFT JOIN FETCH s.exercise
            WHERE s.exercise.id = :exerciseId
            """)
    Set<StaticCodeAnalysisCategory> findWithExerciseByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Links the categories of an exercise with the default category mappings.
     *
     * @param programmingExercise The programming exercise
     * @return A list of pairs of categories and their mappings.
     */
    default List<ImmutablePair<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>>> getCategoriesWithMappingForExercise(
            ProgrammingExercise programmingExercise) {
        var categories = findByExerciseId(programmingExercise.getId());
        var defaultCategories = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(programmingExercise.getProgrammingLanguage());

        List<ImmutablePair<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>>> categoryPairsWithMapping = new ArrayList<>();

        for (var category : categories) {
            var defaultCategoryMatch = defaultCategories.stream().filter(defaultCategory -> defaultCategory.name().equals(category.getName())).findFirst();
            if (defaultCategoryMatch.isPresent()) {
                var categoryMappings = defaultCategoryMatch.get().categoryMappings();
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
     * @param result                     of the build run
     * @param staticCodeAnalysisFeedback List of static code analysis feedback objects
     * @param programmingExercise        The current exercise
     * @return The filtered list of feedback objects
     */
    default List<Feedback> categorizeScaFeedback(Result result, List<Feedback> staticCodeAnalysisFeedback, ProgrammingExercise programmingExercise) {
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
                    if (categoryMappings.stream().anyMatch(mapping -> mapping.tool().name().equals(feedback.getReference()) && mapping.category().equals(issue.getCategory()))) {
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
                    // the feedback is already pre-truncated to fit, it should not be shortened further
                    feedback.setDetailTextTruncated(mapper.writeValueAsString(issue));
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
