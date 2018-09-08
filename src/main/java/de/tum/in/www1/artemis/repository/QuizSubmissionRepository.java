package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuizSubmission;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the QuizSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

}
