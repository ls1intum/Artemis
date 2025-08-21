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

    Optional<BuildRecord> findByBuildId(UUID buildId);

    Optional<BuildRecord> findByParticipationIdOrderByCreatedAtDesc(Long participationId);
}