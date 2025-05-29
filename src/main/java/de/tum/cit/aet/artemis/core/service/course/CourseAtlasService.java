package de.tum.cit.aet.artemis.core.service.course;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Service
@Conditional(AtlasEnabled.class)
public class CourseAtlasService {

    private final CourseRepository courseRepository;

    private final CourseVisibleService courseVisibleService;

    public CourseAtlasService(CourseRepository courseRepository, CourseVisibleService courseVisibleService) {
        this.courseRepository = courseRepository;
        this.courseVisibleService = courseVisibleService;
    }

    /**
     * Get all courses for the given user which have learning paths enabled
     *
     * @param user the user entity
     * @return an unmodifiable set of all courses for the user
     */
    public Set<Course> findAllActiveForUserAndLearningPathsEnabled(User user) {
        return courseRepository.findAllActiveForUserAndLearningPathsEnabled(ZonedDateTime.now()).stream()
                .filter(course -> courseVisibleService.isCourseVisibleForUser(user, course)).collect(Collectors.toSet());
    }
}
