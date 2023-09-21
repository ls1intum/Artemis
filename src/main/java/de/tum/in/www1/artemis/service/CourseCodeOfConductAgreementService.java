package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.CourseCodeOfConductAgreement;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseCodeOfConductAgreementRepository;

/**
 * Service Implementation for managing a user's agreement to a course's code of conduct.
 */
@Service
public class CourseCodeOfConductAgreementService {

    private final CourseCodeOfConductAgreementRepository courseCodeOfConductAgreementRepository;

    CourseCodeOfConductAgreementService(CourseCodeOfConductAgreementRepository courseCodeOfConductAgreementRepository) {
        this.courseCodeOfConductAgreementRepository = courseCodeOfConductAgreementRepository;
    }

    /**
     * Fetches if a user agreed to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     * @return if the user agreed to the course's code of conduct
     */
    public boolean fetchUserAgreesToCodeOfConductInCourse(User user, Course course) {
        return courseCodeOfConductAgreementRepository.findByCourseIdAndUserId(course.getId(), user.getId()).isPresent();
    }

    /**
     * A user agrees to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     */
    public void setUserAgreesToCodeOfConductInCourse(User user, Course course) {
        CourseCodeOfConductAgreement courseCodeOfConductAgreement = new CourseCodeOfConductAgreement();
        courseCodeOfConductAgreement.setCourse(course);
        courseCodeOfConductAgreement.setUser(user);
        courseCodeOfConductAgreementRepository.save(courseCodeOfConductAgreement);
    }

    /**
     * Reset all agreements to a course's code of conduct.
     *
     * @param course the code of conduct's course
     */
    public void resetUsersAgreeToCodeOfConductInCourse(Course course) {
        courseCodeOfConductAgreementRepository.deleteByCourseId(course.getId());
    }
}
