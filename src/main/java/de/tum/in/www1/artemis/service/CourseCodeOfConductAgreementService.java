package de.tum.in.www1.artemis.service;

import java.util.Optional;

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
        Optional<CourseCodeOfConductAgreement> courseCodeOfConductAgreement = courseCodeOfConductAgreementRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        if (courseCodeOfConductAgreement.isPresent()) {
            return courseCodeOfConductAgreement.get().getIsAccepted();
        }
        else {
            CourseCodeOfConductAgreement courseCodeOfConductAgreementNew = new CourseCodeOfConductAgreement();
            courseCodeOfConductAgreementNew.setCourse(course);
            courseCodeOfConductAgreementNew.setUser(user);
            courseCodeOfConductAgreementNew.setIsAccepted(false);
            courseCodeOfConductAgreementRepository.save(courseCodeOfConductAgreementNew);
            return courseCodeOfConductAgreementNew.getIsAccepted();
        }
    }

    /**
     * A user agrees to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     */
    public void setUserAgreesToCodeOfConductInCourse(User user, Course course) {
        CourseCodeOfConductAgreement courseCodeOfConductAgreement = courseCodeOfConductAgreementRepository.findByCourseIdAndUserId(course.getId(), user.getId())
                .orElse(new CourseCodeOfConductAgreement());
        courseCodeOfConductAgreement.setCourse(course);
        courseCodeOfConductAgreement.setUser(user);
        courseCodeOfConductAgreement.setIsAccepted(true);
        courseCodeOfConductAgreementRepository.save(courseCodeOfConductAgreement);
    }
}
