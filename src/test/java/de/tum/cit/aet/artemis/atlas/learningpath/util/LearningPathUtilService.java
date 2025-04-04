package de.tum.cit.aet.artemis.atlas.learningpath.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.atlas.test_repository.LearningPathTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to learning paths for use in integration tests.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
@Conditional(AtlasEnabled.class)
public class LearningPathUtilService {

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private LearningPathTestRepository learningPathRepository;

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
     * Deletes all learning paths of given user.
     *
     * @param user the user for which all learning paths should be deleted
     */
    public void deleteLearningPaths(User user) {
        learningPathRepository.deleteAll(user.getLearningPaths());
    }

}
