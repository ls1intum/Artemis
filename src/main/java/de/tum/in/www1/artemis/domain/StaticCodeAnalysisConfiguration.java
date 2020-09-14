package de.tum.in.www1.artemis.domain;

import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

/**
 * Read-only POJO for storing static code analysis configurations loaded from JSON files
 */
public class StaticCodeAnalysisConfiguration {

    private List<DefaultCategory> defaultCategories;

    public List<DefaultCategory> getDefaultCategories() {
        return defaultCategories;
    }

    public static class DefaultCategory {

        private String name;

        private String description;

        private Integer defaultPenalty;

        private Integer defaultMaxPenalty;

        private CategoryState defaultState;

        private List<CategoryMapping> categoryMappings;

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Integer getDefaultPenalty() {
            return defaultPenalty;
        }

        public Integer getDefaultMaxPenalty() {
            return defaultMaxPenalty;
        }

        public CategoryState getDefaultState() {
            return defaultState;
        }

        public List<CategoryMapping> getCategoryMappings() {
            return categoryMappings;
        }
    }

    public static class CategoryMapping {

        private StaticCodeAnalysisTool tool;

        private String category;

        public StaticCodeAnalysisTool getTool() {
            return tool;
        }

        public String getCategory() {
            return category;
        }
    }
}
