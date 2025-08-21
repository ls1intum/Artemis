package de.tum.cit.aet.artemis.jenkins.connector.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.jenkins.connector.domain.JenkinsProject;

/**
 * Repository for JenkinsProject entities.
 */
@Repository
public interface JenkinsProjectRepository extends JpaRepository<JenkinsProject, Long> {

    /**
     * Finds a Jenkins project by exercise ID.
     *
     * @param exerciseId the exercise ID
     * @return the Jenkins project if found
     */
    Optional<JenkinsProject> findByExerciseId(Long exerciseId);

    /**
     * Finds a Jenkins project by project key.
     *
     * @param projectKey the project key
     * @return the Jenkins project if found
     */
    Optional<JenkinsProject> findByProjectKey(String projectKey);
}