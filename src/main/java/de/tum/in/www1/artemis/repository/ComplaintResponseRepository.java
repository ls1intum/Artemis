package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;

/**
 * Spring Data JPA repository for the ComplaintResponse entity.
 */
@Repository
public interface ComplaintResponseRepository extends JpaRepository<ComplaintResponse, Long> {

    Optional<ComplaintResponse> findByComplaint_Id(Long complaintId);

    /**
     * This magic method counts the number of complaints responses by complaint type associated to a course id
     *
     * @param courseId      - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints response associated to course courseId
     */
    long countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType(Long courseId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints responses by complaint type associated to a exercise id
     *
     * @param exerciseId      - the id of the exercise we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints response associated to exercise exerciseId
     */
    long countByComplaint_Result_Participation_Exercise_Id_AndComplaint_ComplaintType(long exerciseId, ComplaintType complaintType);

    /**
     * Similar to {@link ComplaintResponseRepository#countByComplaint_Result_Participation_Exercise_Id_AndComplaint_ComplaintType}
     * but ignores test run submissions
     * @param exerciseId - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return  number of complaints associated to exercise exerciseId without test runs
     */
    @Query("SELECT COUNT (DISTINCT p) FROM StudentParticipation p WHERE p.exercise.id = :#{#exerciseId} AND EXISTS (Select s FROM p.submissions s where s.results Is not empty and exists (select c from Complaint c where exists (select r.id from s.results r where r.id = c.result.id) and c.complaintType = :#{#complaintType} and exists (select cr from ComplaintResponse cr where cr.complaint.id = c.id))) AND NOT EXISTS (select prs from p.results prs where prs.assessor.id = p.student.id)")
    long countByComplaintResultParticipationExerciseIdAndComplaintComplaintTypeIgnoreTestRuns(long exerciseId, ComplaintType complaintType);

    /**
     * Delete all complaint responses that belong to complaints of submission results of a given participation
     * @param participationId the Id of the participation where the complaint response should be deleted
     */
    void deleteByComplaint_Result_Participation_Id(Long participationId);

    /**
     * Delete all complaint responses that belong to the given result
     * @param resultId the Id of the result where the complaint response should be deleted
     */
    void deleteByComplaint_Result_Id(long resultId);
}
