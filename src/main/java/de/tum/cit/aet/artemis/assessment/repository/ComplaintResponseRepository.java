package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ComplaintResponse entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ComplaintResponseRepository extends ArtemisJpaRepository<ComplaintResponse, Long> {

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
     * @param examId        - the id of the exam we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints response associated to exam examId
     */
    long countByComplaint_Result_Participation_Exercise_ExerciseGroup_Exam_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(Long examId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints responses by complaint type associated to an exercise id
     *
     * @param exerciseId    - the id of the exercise we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints response associated to exercise exerciseId
     */
    @Query("""
            SELECT COUNT (DISTINCT cr)
            FROM ComplaintResponse cr
            WHERE cr.complaint.result.participation.exercise.id = :exerciseId
                AND cr.complaint.complaintType = :complaintType
                AND cr.submittedTime IS NOT NULL
            """)
    long countComplaintResponseByExerciseIdAndComplaintTypeAndSubmittedTimeIsNotNull(@Param("exerciseId") Long exerciseId, @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseIds   - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return List of exercise ids with their number of complaints
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                cr.complaint.result.participation.exercise.id,
                COUNT(DISTINCT cr)
            )
            FROM ComplaintResponse cr
            WHERE cr.complaint.result.participation.exercise.id IN :exerciseIds
                AND cr.submittedTime IS NOT NULL
                AND cr.complaint.complaintType = :complaintType
                AND cr.complaint.result.participation.testRun = FALSE
            GROUP BY cr.complaint.result.participation.exercise.id
            """)
    List<ExerciseMapEntryDTO> countComplaintsByExerciseIdsAndComplaintComplaintTypeIgnoreTestRuns(@Param("exerciseIds") Set<Long> exerciseIds,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * This method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseIds   - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return list of exercise ids with their number of complaints based on the complaint type
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntryDTO(
                cr.complaint.result.participation.exercise.id,
                COUNT(DISTINCT cr)
            )
            FROM ComplaintResponse cr
            WHERE cr.complaint.result.participation.exercise.id IN :exerciseIds
                AND cr.submittedTime IS NOT NULL
                AND cr.complaint.complaintType = :complaintType
            GROUP BY cr.complaint.result.participation.exercise.id
            """)
    List<ExerciseMapEntryDTO> countComplaintsByExerciseIdsAndComplaintComplaintType(@Param("exerciseIds") Set<Long> exerciseIds,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * Delete all complaint responses that belong to the given result
     *
     * @param resultId the id of the result where the complaint response should be deleted
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteByComplaint_Result_Id(long resultId);
}
