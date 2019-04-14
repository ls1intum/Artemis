package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the Submission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    /**
     * return the number of submissions for the given courseId
     */
    long countByParticipation_Exercise_Course_Id(Long courseId);
}
