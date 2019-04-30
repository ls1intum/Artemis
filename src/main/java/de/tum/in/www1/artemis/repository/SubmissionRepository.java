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
     * @param submitted choose which submitted state you want
     * @param exerciseId the id of the exercise you want the stats about
     * @return number of submission for the given exerciseId, with the submitted status expressed by the flag
     */
    long countBySubmittedAndParticipation_Exercise_Id(boolean submitted, Long exerciseId);
}
