package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.TextSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {
    List<TextSubmission> findByIdIn(List<Long> textSubmissionsId);
}
