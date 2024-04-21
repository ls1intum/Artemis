package de.tum.in.www1.artemis.domain;

import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

/**
 * Read-only POJO for storing static code analysis configurations initialized at start-up of Artemis
 */
public record StaticCodeAnalysisDefaultCategory(String name, Double penalty, Double maxPenalty, CategoryState state, List<CategoryMapping> categoryMappings) {

    public record CategoryMapping(StaticCodeAnalysisTool tool, String category) {
    }

    @Override
    public String toString() {
        return "StaticCodeAnalysisDefaultCategory{" + "name='" + name + '\'' + ", penalty=" + penalty + ", maxPenalty=" + maxPenalty + ", state=" + state + '}';
    }
}
