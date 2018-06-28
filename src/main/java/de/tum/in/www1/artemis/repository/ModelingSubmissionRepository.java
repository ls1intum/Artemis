package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ModelingSubmission;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the ModelingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingSubmissionRepository extends JpaRepository<ModelingSubmission, Long> {

}
