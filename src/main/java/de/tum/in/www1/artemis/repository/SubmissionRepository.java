package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * Spring Data repository for the Submission entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * @param submitted  choose which submitted state you want
     * @param exerciseId the id of the exercise you want the stats about
     * @return number of submission for the given exerciseId, with the submitted status expressed by the flag
     */
    long countBySubmittedAndParticipation_Exercise_Id(boolean submitted, Long exerciseId);

    @Query("select submission from Submission submission where type(submission) in (ModelingSubmission, TextSubmission) and submission.submitted = false")
    List<Submission> findAllUnsubmittedModelingAndTextSubmissions();
}
