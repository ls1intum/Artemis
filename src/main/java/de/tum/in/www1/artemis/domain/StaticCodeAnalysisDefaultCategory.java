package de.tum.in.www1.artemis.domain;

import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

/**
 * Read-only POJO for storing static code analysis configurations initialized at start-up of Artemis
 */
public class StaticCodeAnalysisDefaultCategory {

    private final String name;

    private final Double penalty;

    private final Double maxPenalty;

    private final CategoryState state;

    private final List<CategoryMapping> categoryMappings;

    public StaticCodeAnalysisDefaultCategory(String name, Double penalty, Double maxPenalty, CategoryState state, List<CategoryMapping> categoryMappings) {
        this.name = name;
        this.penalty = penalty;
        this.maxPenalty = maxPenalty;
        this.state = state;
        this.categoryMappings = categoryMappings;
    }

    public String getName() {
        return name;
    }

    public Double getPenalty() {
        return penalty;
    }

    public Double getMaxPenalty() {
        return maxPenalty;
    }

    public CategoryState getState() {
        return state;
    }

    public List<CategoryMapping> getCategoryMappings() {
        return categoryMappings;
    }

    public static class CategoryMapping {

        private final StaticCodeAnalysisTool tool;

        private final String category;

        public CategoryMapping(StaticCodeAnalysisTool tool, String category) {
            this.tool = tool;
            this.category = category;
        }

        public StaticCodeAnalysisTool getTool() {
            return tool;
        }

        public String getCategory() {
            return category;
        }
    }

    @Override
    public String toString() {
        return "StaticCodeAnalysisDefaultCategory{" + "name='" + name + '\'' + ", penalty=" + penalty + ", maxPenalty=" + maxPenalty + ", state=" + state + '}';
    }
}
