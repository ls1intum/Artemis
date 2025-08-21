package de.tum.cit.aet.artemis.jenkins.connector.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.jenkins.connector.domain.BuildRecord;

/**
 * Repository for BuildRecord entities.
 */
@Repository
public interface BuildRecordRepository extends JpaRepository<BuildRecord, Long> {

    /**
     * Finds a build record by its UUID.
     *
     * @param buildUuid the build UUID
     * @return the build record if found
     */
    Optional<BuildRecord> findByBuildUuid(UUID buildUuid);

    /**
     * Finds the latest build record for a participation.
     *
     * @param exerciseId the exercise ID
     * @param participationId the participation ID
     * @return the latest build record if found
     */
    Optional<BuildRecord> findTopByExerciseIdAndParticipationIdOrderByCreatedAtDesc(Long exerciseId, Long participationId);
}