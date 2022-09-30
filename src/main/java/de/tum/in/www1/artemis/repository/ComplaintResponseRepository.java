package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry;
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
    long countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(Long courseId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints responses by complaint type associated to an exam id
     *
     * @param examId      - the id of the exam we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints response associated to exam examId
     */
    long countByComplaint_Result_Participation_Exercise_ExerciseGroup_Exam_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(Long examId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints responses by complaint type associated to an exercise id
     *
     * @param exerciseId      - the id of the exercise we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints response associated to exercise exerciseId
     */
    @Query("""
            SELECT COUNT (DISTINCT cr) FROM ComplaintResponse cr
                WHERE cr.complaint.result.participation.exercise.id = :exerciseId
                AND cr.complaint.complaintType = :complaintType
                AND cr.submittedTime IS NOT NULL
            """)
    long countComplaintResponseByExerciseIdAndComplaintTypeAndSubmittedTimeIsNotNull(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseIds    - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId
     */
    @Query("""
                SELECT
                    new de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                        cr.complaint.result.participation.exercise.id,
                        count(DISTINCT cr)
                    )
                FROM ComplaintResponse cr
                WHERE cr.complaint.result.participation.exercise.id IN (:exerciseIds)
                    AND cr.submittedTime IS NOT NULL
                    AND cr.complaint.complaintType = :complaintType
                    AND cr.complaint.result.participation.testRun = FALSE
                GROUP BY cr.complaint.result.participation.exercise.id
            """)
    List<ExerciseMapEntry> countComplaintsByExerciseIdsAndComplaintComplaintTypeIgnoreTestRuns(@Param("exerciseIds") Set<Long> exerciseIds,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseIds    - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId
     */
    @Query("""
                SELECT
                    new de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry(
                        cr.complaint.result.participation.exercise.id,
                        count(DISTINCT cr)
                    )
                FROM ComplaintResponse cr
                WHERE cr.complaint.result.participation.exercise.id IN (:exerciseIds)
                    AND cr.submittedTime IS NOT NULL
                    AND cr.complaint.complaintType = :complaintType
                GROUP BY cr.complaint.result.participation.exercise.id
            """)
    List<ExerciseMapEntry> countComplaintsByExerciseIdsAndComplaintComplaintType(@Param("exerciseIds") Set<Long> exerciseIds, @Param("complaintType") ComplaintType complaintType);

    /**
     * Delete all complaint responses that belong to the given result
     * @param resultId the id of the result where the complaint response should be deleted
     */
    @Transactional
    @Modifying
    void deleteByComplaint_Result_Id(long resultId);
}
