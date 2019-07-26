package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingSubmissionRepository extends JpaRepository<ProgrammingSubmission, Long> {

    @EntityGraph(attributePaths = { "result" })
    ProgrammingSubmission findFirstByParticipationIdAndCommitHash(Long participationId, String commitHash);
}
