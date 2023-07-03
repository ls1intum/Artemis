package de.tum.in.www1.artemis.competency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.service.LearningPathService;

/**
 * Service responsible for initializing the database with specific testdata related to learning paths for use in integration tests.
 */
@Service
public class LearningPathUtilService {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    LearningPathService learningPathService;

    /**
     * Enable and generate learning paths for course.
     *
     * @param course the course for which the learning paths are generated
     * @return the updated course
     */
    public Course enableAndGenerateLearningPathsForCourse(Course course) {
        course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(course.getId());
        learningPathService.generateLearningPaths(course);
        course.setLeanringPathsEnabled(true);
        return courseRepository.save(course);
    }

}
