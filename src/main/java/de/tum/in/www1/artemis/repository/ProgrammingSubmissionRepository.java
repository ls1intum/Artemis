package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ProgrammingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ProgrammingSubmissionRepository extends JpaRepository<ProgrammingSubmission, Long> {

    ProgrammingSubmission findFirstByParticipationIdAndCommitHash(Long participationId, String commitHash);
}
