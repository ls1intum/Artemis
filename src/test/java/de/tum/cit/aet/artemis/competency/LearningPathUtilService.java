package de.tum.cit.aet.artemis.competency;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Service responsible for initializing the database with specific testdata related to learning paths for use in integration tests.
 */
@Service
public class LearningPathUtilService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private LearningPathRepository learningPathRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CourseCompetencyRepository courseCompetencyRepository;

    /**
     * Enable and generate learning paths for course.
     *
     * @param course the course for which the learning paths are generated
     * @return the updated course
     */
    public Course enableAndGenerateLearningPathsForCourse(Course course) {
        var eagerlyLoadedCourse = courseRepository.findWithEagerLearningPathsAndCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
        learningPathService.generateLearningPaths(eagerlyLoadedCourse);
        eagerlyLoadedCourse.setLearningPathsEnabled(true);
        return courseRepository.save(eagerlyLoadedCourse);
    }

    /**
     * Creates learning path for course.
     *
     * @param course the course for which the learning path should be generated
     * @return the persisted learning path
     */
    public LearningPath createLearningPathInCourse(Course course) {
        final var competencies = courseCompetencyRepository.findAllForCourse(course.getId());
        LearningPath learningPath = createLearningPath(competencies);
        learningPath.setCourse(course);
        return learningPathRepository.save(learningPath);
    }

    /**
     * Creates learning path for given user in course.
     *
     * @param course the course for which the learning path should be generated
     * @return the persisted learning path
     */
    public LearningPath createLearningPathInCourseForUser(Course course, User user) {
        final var learningPath = createLearningPathInCourse(course);
        learningPath.setUser(user);
        return learningPathRepository.save(learningPath);
    }

    /**
     * Creates learning path.
     *
     * @param competencies the competencies that will be linked to the learning path
     * @return the persisted learning path
     */
    public LearningPath createLearningPath(Set<CourseCompetency> competencies) {
        LearningPath learningPath = new LearningPath();
        learningPath.setCompetencies(competencies);
        return learningPathRepository.save(learningPath);
    }

    /**
     * Deletes all learning paths of given user.
     *
     * @param user the user for which all learning paths should be deleted
     */
    public void deleteLearningPaths(User user) {
        learningPathRepository.deleteAll(user.getLearningPaths());
    }

    /**
     * Deletes all learning paths of all given user.
     *
     * @param users the users for which all learning paths should be deleted
     */
    public void deleteLearningPaths(Iterable<User> users) {
        users.forEach(this::deleteLearningPaths);
    }
}
