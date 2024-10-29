package de.tum.cit.aet.artemis.programming.domain;

import java.util.List;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;

/**
 * Read-only POJO for storing static code analysis configurations initialized at start-up of Artemis
 */
public record StaticCodeAnalysisDefaultCategory(String name, Double penalty, Double maxPenalty, CategoryState state, List<CategoryMapping> categoryMappings) {

    public record CategoryMapping(StaticCodeAnalysisTool tool, String category) {
    }

    /**
     * Create a new StaticCodeAnalysisCategory using the provided default settings.
     *
     * @param programmingExercise the programming exercise to link the created category with.
     * @return new StaticCodeAnalysisCategory object initialized with default settings.
     */
    public StaticCodeAnalysisCategory toStaticCodeAnalysisCategory(ProgrammingExercise programmingExercise) {
        StaticCodeAnalysisCategory newCategory = new StaticCodeAnalysisCategory();
        newCategory.setName(name());
        newCategory.setPenalty(penalty());
        newCategory.setMaxPenalty(maxPenalty());
        newCategory.setState(state());
        newCategory.setProgrammingExercise(programmingExercise);
        return newCategory;
    }

    @Override
    public String toString() {
        return "StaticCodeAnalysisDefaultCategory{" + "name='" + name + '\'' + ", penalty=" + penalty + ", maxPenalty=" + maxPenalty + ", state=" + state + '}';
    }
}
