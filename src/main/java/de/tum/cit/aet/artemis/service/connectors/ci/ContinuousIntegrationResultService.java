package de.tum.cit.aet.artemis.service.connectors.ci;

import java.util.List;

import de.tum.cit.aet.artemis.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.service.dto.AbstractBuildResultNotificationDTO;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface ContinuousIntegrationResultService {

    /**
     * converts the object from the CI system (Jenkins, or GitLabCI) into a proper Java DTO
     *
     * @param requestBody the object sent from the CI system to Artemis
     * @return the DTO with all information in Java Object form
     */
    AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody);

    /**
     * Generate an Artemis result object from the CI build result. Will use the test case results and issues in static code analysis as result feedback.
     *
     * @param buildResult   Build result data provided by build notification (already converted into a DTO)
     * @param participation to attach result to.
     * @return the created Artemis result with a score, completion date, etc.
     */
    Result createResultFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExerciseParticipation participation);

    /**
     * Extract the build log statistics from the BuildLogEntries and persist a BuildLogStatisticsEntry.
     * Not all programming languages and project types might be supported on all implementations of the ContinuousIntegrationService.
     *
     * @param programmingSubmission the submission to which the generated BuildLogStatisticsEntry should be attached
     * @param programmingLanguage   the programming language of the programming exercise
     * @param projectType           the project type of the programming exercise
     * @param buildLogEntries       the list of BuildLogEntries received from the CI-Server
     */
    void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries);
}
