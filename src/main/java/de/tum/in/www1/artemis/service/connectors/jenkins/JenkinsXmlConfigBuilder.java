package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.util.Optional;

import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

public interface JenkinsXmlConfigBuilder {

    /**
     * Contains the URLs required to set up the build plan.
     * <p>
     * The URLs should haven been converted to the internal URL format using {@link JenkinsInternalUrlService#toInternalVcsUrl(VcsRepositoryUrl)}.
     *
     * @param assignmentRepositoryUrl The URL to the VCS repository of the student.
     * @param testRepositoryUrl       The URL to the VCS repository of the tests for the exercise.
     * @param solutionRepositoryUrl   The URL to the VCS repository of the solution of the exercise.
     */
    record InternalVcsRepositoryURLs(VcsRepositoryUrl assignmentRepositoryUrl, VcsRepositoryUrl testRepositoryUrl, VcsRepositoryUrl solutionRepositoryUrl) {
    }

    /**
     * Creates a basic build config for Jenkins based on the given repository URLs. I.e. a build that tests the assignment
     * code and exports the build results to Artemis afterwards. If static code analysis is activated, the plan will additionally
     * execute supported static code analysis tools.
     *
     * @param programmingLanguage The programming language for which the config should be generated
     * @param projectType The optional project type of the exercise.
     * @param internalVcsRepositoryURLs The URLs of the source code repositories that are required to set up the build plan.
     * @param isStaticCodeAnalysisEnabled Flag which determines whether a build plan with or without static code analysis is created
     * @param isSequentialRuns Should activate sequential test runs options
     * @return The parsed XML document containing the Jenkins build config
     */
    Document buildBasicConfig(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, InternalVcsRepositoryURLs internalVcsRepositoryURLs,
            boolean isStaticCodeAnalysisEnabled, boolean isSequentialRuns);
}
