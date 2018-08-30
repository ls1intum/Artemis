package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.TextSubmission;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

}
