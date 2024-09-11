package de.tum.cit.aet.artemis.service.programming;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Store configuration of a specific programming language.
 * The configuration is also available in the client as the {@link ProgrammingLanguageFeatureService} exposes them.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingLanguageFeature(ProgrammingLanguage programmingLanguage, boolean sequentialTestRuns, boolean staticCodeAnalysis, boolean plagiarismCheckSupported,
        boolean packageNameRequired, boolean checkoutSolutionRepositoryAllowed, List<ProjectType> projectTypes, boolean testwiseCoverageAnalysisSupported,
        boolean auxiliaryRepositoriesSupported) {
}
