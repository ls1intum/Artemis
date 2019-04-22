package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * Spring Data repository for the Submission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * return the number of submissions for the given courseId
     */
    long countByParticipation_Exercise_Course_IdAndSubmitted(Long courseId, Boolean submitted);
}
