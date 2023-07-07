package de.tum.in.www1.artemis.competency;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
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

    @Autowired
    LearningPathRepository learningPathRepository;

    @Autowired
    CompetencyRepository competencyRepository;

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

    public LearningPath createLearningPathInCourse(Course course) {
        final var competencies = competencyRepository.findAllForCourse(course.getId());
        LearningPath learningPath = createLearningPath(competencies);
        learningPath.setCourse(course);
        return learningPathRepository.save(learningPath);
    }

    public LearningPath createLearningPath(Set<Competency> competencies) {
        LearningPath lp = new LearningPath();
        lp.setCompetencies(competencies);
        return learningPathRepository.save(lp);
    }
}
