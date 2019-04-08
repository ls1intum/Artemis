package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ComplaintResponse;

/**
 * Spring Data JPA repository for the ComplaintResponse entity.
 */
@Repository
public interface ComplaintResponseRepository extends JpaRepository<ComplaintResponse, Long> {

    /**
     * This magic method counts the number of complaints responses associated to a course id
     *
     * @param courseId - the id of the course we want to filter by
     * @return number of complaints response associated to course courseId
     */
    long countByComplaint_Result_Participation_Exercise_Course_Id(Long courseId);
}
