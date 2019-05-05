package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextSubmission;

/**
 * Spring Data JPA repository for the TextSubmission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextSubmissionRepository extends JpaRepository<TextSubmission, Long> {

    List<TextSubmission> findByIdIn(List<Long> textSubmissionsId);

    long countBySubmittedAndParticipation_Exercise_Id(boolean submitted, long exerciseId);

    /**
     * @param courseId the course we are interested in
     * @param submitted boolean to check if an exercise has been submitted or not
     * @return number of submissions belonging to courseId with submitted status
     */
    long countByParticipation_Exercise_Course_IdAndSubmitted(Long courseId, boolean submitted);
}
