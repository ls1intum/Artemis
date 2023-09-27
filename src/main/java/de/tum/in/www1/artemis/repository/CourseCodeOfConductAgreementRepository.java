package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.CourseCodeOfConductAgreement;
import de.tum.in.www1.artemis.domain.CourseCodeOfConductAgreementId;

/**
 * Spring Data repository for the Code of Conduct Agreement entity.
 */
@Repository
public interface CourseCodeOfConductAgreementRepository extends JpaRepository<CourseCodeOfConductAgreement, CourseCodeOfConductAgreementId> {

    /**
     * Find the user's agreement to a course's code of conduct.
     *
     * @param courseId the ID of the code of conduct's course
     * @param userId   the user's ID
     * @return the user's agreement to the course's code of conduct
     */
    Optional<CourseCodeOfConductAgreement> findByCourseIdAndUserId(Long courseId, Long userId);

    /**
     * Delete all users' agreements to a course's code of conduct.
     *
     * @param courseId the ID of the code of conduct's course
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteByCourseId(Long courseId);
}
