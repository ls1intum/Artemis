package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.ConductAgreement;
import de.tum.cit.aet.artemis.communication.repository.ConductAgreementRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Service Implementation for managing a user's agreement to a course's code of conduct.
 */
@Profile(PROFILE_CORE)
@Service
public class ConductAgreementService {

    private final ConductAgreementRepository conductAgreementRepository;

    public ConductAgreementService(ConductAgreementRepository conductAgreementRepository) {
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
