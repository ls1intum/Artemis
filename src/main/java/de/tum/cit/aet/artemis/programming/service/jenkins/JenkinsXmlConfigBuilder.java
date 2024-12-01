package de.tum.cit.aet.artemis.programming.service.jenkins;

import java.util.Optional;

import org.w3c.dom.Document;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

public interface JenkinsXmlConfigBuilder {

    /**
     * Contains the URLs required to set up the build plan.
     * <p>
     * The URLs should haven been converted to the internal URL format using {@link JenkinsInternalUrlService#toInternalVcsUrl(VcsRepositoryUri)}.
     *
     * @param assignmentRepositoryUri The URL to the VCS repository of the student.
     * @param testRepositoryUri       The URL to the VCS repository of the tests for the exercise.
     * @param solutionRepositoryUri   The URL to the VCS repository of the solution of the exercise.
     */
    record InternalVcsRepositoryURLs(VcsRepositoryUri assignmentRepositoryUri, VcsRepositoryUri testRepositoryUri, VcsRepositoryUri solutionRepositoryUri) {
    }

    /**
     * Creates a basic build config for Jenkins based on the given repository URIs. I.e. a build that tests the assignment
     * code and exports the build results to Artemis afterwards. If static code analysis is activated, the plan will additionally
     * execute supported static code analysis tools.
     *
     * @param programmingLanguage       The programming language for which the config should be generated
     * @param projectType               The optional project type of the exercise.
     * @param internalVcsRepositoryURLs The URLs of the source code repositories that are required to set up the build plan.
     * @param isSolutionPlan            True, if this is the build plan config for the solution repository.
     * @param buildPlanUrl              The URL to the build plan for this exercise.
     * @return The parsed XML document containing the Jenkins build config
     */
    Document buildBasicConfig(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, InternalVcsRepositoryURLs internalVcsRepositoryURLs,
            boolean isSolutionPlan, String buildPlanUrl);
}
