package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Complaint;

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
     * This magic method counts the number of complaints associated to a course id
     *
     * @param courseId - the id of the course we want to filter by
     * @return number of complaints associated to course courseId
     */
    long countByResult_Participation_Exercise_Course_Id(Long courseId);

    @Query("SELECT c FROM Complaint c LEFT JOIN FETCH c.result r LEFT JOIN FETCH r.assessor LEFT JOIN FETCH r.participation p LEFT JOIN FETCH p.exercise e LEFT JOIN FETCH r.submission WHERE e.id = :#{#exerciseId}")
    Optional<List<Complaint>> findByResult_Participation_Exercise_IdWithEagerSubmissionAndEagerAssessor(@Param("exerciseId") Long exerciseId);

    /**
     * Count the number of unaccepted complaints of a student in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the number
     * of complaints for a student in a course.
     *
     * @param studentId the id of the student
     * @param courseId  the id of the course
     * @return the number of unaccepted
     */
    @Query("SELECT count(c) FROM Complaint c  WHERE c.student.id = :#{#studentId} AND c.result.participation.exercise.course.id = :#{#courseId} AND (c.accepted = false OR c.accepted is null)")
    long countUnacceptedComplaintsByStudentIdAndCourseId(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /**
     * This magic method counts the number of complaints associated to an exercise id
     *
     * @param exerciseId - the id of the course we want to filter by
     * @return number of complaints associated to exercise exerciseId
     */
    long countByResult_Participation_Exercise_Id(Long exerciseId);

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
     */
    void deleteByResult_Participation_Id(Long participationId);
}
