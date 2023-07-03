package de.tum.in.www1.artemis.competency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.service.LearningPathService;

@Service
public class LearningPathUtilService {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    LearningPathService learningPathService;

    public Course enableAndGenerateLearningPathsForCourse(Course course) {
        course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(course.getId());
        learningPathService.generateLearningPaths(course);
        course.setLeanringPathsEnabled(true);
        return courseRepository.save(course);
    }

}
