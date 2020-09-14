package de.tum.in.www1.artemis.domain;

import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

/**
 * POJO for storing static code analysis configurations read from JSON files
 */
public class StaticCodeAnalysisConfiguration {

    private List<DefaultCategory> defaultCategories;

    public List<DefaultCategory> getDefaultCategories() {
        return defaultCategories;
    }

    public void setDefaultCategories(List<DefaultCategory> defaultCategories) {
        this.defaultCategories = defaultCategories;
    }

    public class DefaultCategory {

        private String name;

        private Integer defaultPenalty;

        private Integer defaultMaxPenalty;

        private CategoryState defaultState;

        private List<CategoryMapping> categoryMappings;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getDefaultPenalty() {
            return defaultPenalty;
        }

        public void setDefaultPenalty(Integer defaultPenalty) {
            this.defaultPenalty = defaultPenalty;
        }

        public Integer getDefaultMaxPenalty() {
            return defaultMaxPenalty;
        }

        public void setDefaultMaxPenalty(Integer defaultMaxPenalty) {
            this.defaultMaxPenalty = defaultMaxPenalty;
        }

        public CategoryState getDefaultState() {
            return defaultState;
        }

        public void setDefaultState(CategoryState defaultState) {
            this.defaultState = defaultState;
        }

        public List<CategoryMapping> getCategoryMappings() {
            return categoryMappings;
        }

        public void setCategoryMappings(List<CategoryMapping> categoryMappings) {
            this.categoryMappings = categoryMappings;
        }
    }

    public static class CategoryMapping {

        private StaticCodeAnalysisTool tool;

        private String category;

        public StaticCodeAnalysisTool getTool() {
            return tool;
        }

        public void setTool(StaticCodeAnalysisTool tool) {
            this.tool = tool;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}
