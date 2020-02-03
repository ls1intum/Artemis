package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByResult_Id(Long resultId);

    @Query("SELECT c FROM Complaint c LEFT JOIN FETCH c.result r LEFT JOIN FETCH r.assessor WHERE c.id = :#{#complaintId}")
    Optional<Complaint> findByIdWithEagerAssessor(@Param("complaintId") Long complaintId);

    /**
     * This magic method counts the number of complaints associated to a course id and to the results assessed by a specific user, identified by a tutor id
     *
     * @param courseId - the id of the course we want to filter by
     * @param tutorId  - the id of the tutor we are interested in
     * @return number of complaints associated to course courseId and tutor tutorId
     */
    long countByResult_Participation_Exercise_Course_IdAndResult_Assessor_Id(Long courseId, Long tutorId);

    /**
     * This magic method counts the number of complaints by complaint type associated to a course id
     *
     * @param courseId      - the id of the course we want to filter by
     * @param complaintType - type of complaint we want to filter by
     * @return number of more feedback requests associated to course courseId
     */
    long countByResult_Participation_Exercise_Course_IdAndComplaintType(Long courseId, ComplaintType complaintType);

    @Query("SELECT c FROM Complaint c LEFT JOIN FETCH c.result r LEFT JOIN FETCH r.assessor LEFT JOIN FETCH r.participation p LEFT JOIN FETCH p.exercise e LEFT JOIN FETCH r.submission WHERE e.id = :#{#exerciseId} AND c.complaintType = :#{#complaintType}")
    List<Complaint> findByResult_Participation_Exercise_Id_ComplaintTypeWithEagerSubmissionAndEagerAssessor(@Param("exerciseId") Long exerciseId,
            @Param("complaintType") ComplaintType complaintType);

    /**
     * Count the number of unaccepted complaints of a student in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a student in a course. Requests for more feedback are not counted here.
     *
     * @param studentId the id of the student
     * @param courseId  the id of the course
     * @return the number of unaccepted
     */
    @Query("SELECT count(c) FROM Complaint c WHERE c.complaintType = 'COMPLAINT' AND c.student.id = :#{#studentId} AND c.result.participation.exercise.course.id = :#{#courseId} AND (c.accepted = false OR c.accepted is null)")
    long countUnacceptedComplaintsByComplaintTypeStudentIdAndCourseId(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /**
     * This magic method counts the number of complaints by complaint type associated to an exercise id
     *
     * @param exerciseId    - the id of the course we want to filter by
     * @param complaintType - complaint type we want to filter by
     * @return number of complaints associated to exercise exerciseId
     */

    long countByResult_Participation_Exercise_IdAndComplaintType(Long exerciseId, ComplaintType complaintType);

    /**
     * This magic method counts the number of complaints associated to a exercise id and to the results assessed by a specific user, identified by a tutor id
     *
     * @param exerciseId - the id of the exercise we want to filter by
     * @param tutorId    - the id of the tutor we are interested in
     * @return number of complaints associated to exercise exerciseId and tutor tutorId
     */
    long countByResult_Participation_Exercise_IdAndResult_Assessor_Id(Long exerciseId, Long tutorId);

    /**
     * Delete all complaints that belong to submission results of a given participation
     * @param participationId the Id of the participation where the complaints should be deleted
     */
    void deleteByResult_Participation_Id(Long participationId);

    /**
     * Delete all complaints that belong to the given result
     * @param resultId the Id of the result where the complaints should be deleted
     */
    void deleteByResult_Id(long resultId);

    /**
     * Given a user id, retrieve all complaints related to assessments made by that assessor
     *
     * @param assessorId - the id of the assessor
     * @return a list of complaints
     */
    @EntityGraph(attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Assessor_Id(Long assessorId);

    /**
     * Given a exercise id, retrieve all complaints related to that exercise
     *
     * @param exerciseId - the id of the exercise
     * @return a list of complaints
     */
    @EntityGraph(attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Participation_Exercise_Id(Long exerciseId);

    /**
     * Given a course id, retrieve all complaints related to assessments related to that course
     *
     * @param courseId - the id of the course
     * @return a list of complaints
     */
    @EntityGraph(attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Participation_Exercise_Course_Id(Long courseId);

    /**
     * Given a user id and an exercise id retrieve all complaints related to assessments made by that assessor in that exercise.
     *
     * @param assessorId - the id of the assessor
     * @param exerciseId - the id of the exercise
     * @return a list of complaints
     */
    @EntityGraph(attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Assessor_IdAndResult_Participation_Exercise_Id(Long assessorId, Long exerciseId);

    /**
     * Given a user id and a course id retrieve all complaints related to assessments made by that assessor in that course.
     *
     * @param assessorId - the id of the assessor
     * @param courseId   - the id of the course
     * @return a list of complaints
     */
    @EntityGraph(attributePaths = { "result.participation", "result.submission", "result.assessor" })
    List<Complaint> getAllByResult_Assessor_IdAndResult_Participation_Exercise_Course_Id(Long assessorId, Long courseId);
}
