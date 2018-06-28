package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Submission;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the Submission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

}
