package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Complaint;

/**
 * Spring Data JPA repository for the Complaint entity.
 */
@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByResult_Id(Long resultId);

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
}
