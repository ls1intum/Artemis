package de.tum.in.www1.artemis.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.CourseCodeOfConduct;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseCodeOfConductRepository;

/**
 * Service Implementation for managing a course's code of conduct.
 */
@Service
public class CourseCodeOfConductService {

    private final CourseCodeOfConductRepository courseCodeOfConductRepository;

    CourseCodeOfConductService(CourseCodeOfConductRepository courseCodeOfConductRepository) {
        this.courseCodeOfConductRepository = courseCodeOfConductRepository;
    }

    /**
     * Fetches if a user agreed to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     * @return if the user agreed to the course's code of conduct
     */
    public boolean fetchUserAgreesToCodeOfConductInCourse(User user, Course course) {
        Optional<CourseCodeOfConduct> courseCodeOfConduct = courseCodeOfConductRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        if (courseCodeOfConduct.isPresent()) {
            return courseCodeOfConduct.get().getIsCodeOfConductAccepted();
        }
        else {
            CourseCodeOfConduct courseCodeOfConductNew = new CourseCodeOfConduct();
            courseCodeOfConductNew.setCourse(course);
            courseCodeOfConductNew.setUser(user);
            courseCodeOfConductNew.setIsCodeOfConductAccepted(false);
            courseCodeOfConductRepository.save(courseCodeOfConductNew);
            return courseCodeOfConductNew.getIsCodeOfConductAccepted();
        }
    }

    /**
     * A user agrees to a course's code of conduct.
     *
     * @param user   the user in the course
     * @param course the code of conduct's course
     */
    public void setUserAgreesToCodeOfConductInCourse(User user, Course course) {
        CourseCodeOfConduct courseCodeOfConduct = courseCodeOfConductRepository.findByCourseIdAndUserId(course.getId(), user.getId()).orElse(new CourseCodeOfConduct());
        courseCodeOfConduct.setCourse(course);
        courseCodeOfConduct.setUser(user);
        courseCodeOfConduct.setIsCodeOfConductAccepted(true);
        courseCodeOfConductRepository.save(courseCodeOfConduct);
    }
}
