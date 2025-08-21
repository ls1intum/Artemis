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

    Optional<JenkinsProject> findByProjectKey(String projectKey);

    Optional<JenkinsProject> findByExerciseId(Long exerciseId);
}