package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ConductAgreement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ConductAgreementRepository;

/**
 * Service Implementation for managing a user's agreement to a course's code of conduct.
 */
@Service
public class ConductAgreementService {

    private final ConductAgreementRepository conductAgreementRepository;

    ConductAgreementService(ConductAgreementRepository conductAgreementRepository) {
        this.conductAgreementRepository = conductAgreementRepository;
    }

    /**
     * Fetches if a user agreed to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     * @return if the user agreed to the course's code of conduct
     */
    public boolean fetchUserAgreesToCodeOfConductInCourse(User user, Course course) {
        var codeOfConduct = course.getCourseInformationSharingMessagingCodeOfConduct();
        if (codeOfConduct == null || codeOfConduct.isEmpty()) {
            return true;
        }
        return conductAgreementRepository.findByCourseIdAndUserId(course.getId(), user.getId()).isPresent();
    }

    /**
     * A user agrees to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     */
    public void setUserAgreesToCodeOfConductInCourse(User user, Course course) {
        ConductAgreement conductAgreement = new ConductAgreement();
        conductAgreement.setCourse(course);
        conductAgreement.setUser(user);
        conductAgreementRepository.save(conductAgreement);
    }

    /**
     * Reset all agreements to a course's code of conduct.
     *
     * @param course the code of conduct's course
     */
    public void resetUsersAgreeToCodeOfConductInCourse(Course course) {
        conductAgreementRepository.deleteByCourseId(course.getId());
    }
}
