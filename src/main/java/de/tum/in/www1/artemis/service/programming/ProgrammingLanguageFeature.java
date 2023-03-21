package de.tum.in.www1.artemis.service.programming;

import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

/**
 * Store configuration of a specific programming language.
 * The configuration is also available in the client as the {@link ProgrammingLanguageFeatureContributor} exposes them.
 */
public record ProgrammingLanguageFeature(ProgrammingLanguage programmingLanguage, boolean sequentialTestRuns, boolean staticCodeAnalysis, boolean plagiarismCheckSupported,
        boolean packageNameRequired, boolean checkoutSolutionRepositoryAllowed, List<ProjectType> projectTypes, boolean testwiseCoverageAnalysisSupported,
        boolean publishBuildPlanUrlAllowed, boolean auxiliaryRepositoriesSupported) {

}
