package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Service for migrations affecting a continuous integration system and version control system.
 * Every migration might now be implemented for all CI and VCS combinations systems.
 */
public interface CIVCSMigrationService {

    /**
     * Overrides the existing notification URL for build results with the current one in use by Artemis.
     *
     * @param projectKey    The key of the project, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param buildPlanKey  The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param repositoryUri the URI of the assignment repository
     */
    void overrideBuildPlanNotification(String projectKey, String buildPlanKey, VcsRepositoryUri repositoryUri);

    /**
     * Deletes all build triggers for the given build plan.
     *
     * @param projectKey    The key of the project, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param buildPlanKey  The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param repositoryUri the URI of the repository that triggers the build
     */
    void deleteBuildTriggers(String projectKey, String buildPlanKey, VcsRepositoryUri repositoryUri);

    /**
     * Overrides the existing repository URI for the given build plan.
     *
     * @param buildPlanKey  The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-BASE'.
     * @param name          The name of the repository
     * @param repositoryUri the URI of the repository
     * @param defaultBranch the default branch of the exercise to be migrated
     */
    void overrideBuildPlanRepository(String buildPlanKey, String name, String repositoryUri, String defaultBranch);

    /**
     * Overrides the existing repository URI for the given project that are checked out by the build plans.
     *
     * @param buildPlanKey            The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-BASE'.
     * @param auxiliaryRepositoryList the list of auxiliary repositories
     * @param programmingLanguage     the programming language of the exercise to be migrated
     */
    void overrideRepositoriesToCheckout(String buildPlanKey, List<AuxiliaryRepository> auxiliaryRepositoryList, ProgrammingLanguage programmingLanguage);

    /**
     * Jenkins and Bamboo need different results, this method is used to get the correct participations.
     *
     * @param programmingExerciseStudentParticipationRepository the repository to get the participations from
     * @param pageable                                          the pageable object
     * @return the page of participations
     */
    Page<ProgrammingExerciseStudentParticipation> getPageableStudentParticipations(
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Pageable pageable);

    /**
     * Checks if the migration service supports auxiliary repositories. If not, the auxiliary repositories will be ignored
     * and database calls can be reduced.
     *
     * @return true if the migration service supports auxiliary repositories, false otherwise
     */
    boolean supportsAuxiliaryRepositories();

    /**
     * Remove the web hooks for the given repository.
     *
     * @param repositoryUri the repository uri for which the webhook should be removed
     */
    void removeWebHook(VcsRepositoryUri repositoryUri);

    /**
     * Checks if the build plan exists.
     *
     * @param projectKey   The key of the project, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param buildPlanKey The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @return true if the build plan exists in the CI system, false otherwise
     */
    boolean buildPlanExists(String projectKey, String buildPlanKey);

    /**
     * Checks if the prerequisites for the migration are met.
     */
    void checkPrerequisites() throws ContinuousIntegrationException;
}
