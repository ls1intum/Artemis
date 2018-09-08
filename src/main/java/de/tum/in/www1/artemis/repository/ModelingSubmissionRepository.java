package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ModelingSubmission;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ModelingSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelingSubmissionRepository extends JpaRepository<ModelingSubmission, Long> {

}
