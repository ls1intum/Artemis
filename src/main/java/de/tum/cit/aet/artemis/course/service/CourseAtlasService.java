package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * Service for retrieving courses that are visible to users and have learning paths enabled.
 * This is only used in the atlas module.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseAtlasService {

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public CourseAtlasService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService) {
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Get all courses for the given user which have learning paths enabled.
     * <p>
     * For admins, all active learning-path courses are returned — admins can see every course
     * and the query already filters by date range and learningPathsEnabled, so no additional
     * visibility check is needed.
     * For non-admins, the membership filter is pushed into the query via an indexed join so only
     * the user's own courses are loaded instead of all learning-path courses.
     *
     * @param user the user entity
     * @return an unmodifiable set of all courses for the user
     */
    public Set<Course> findAllActiveForUserAndLearningPathsEnabled(User user) {
        ZonedDateTime now = ZonedDateTime.now();
        if (authorizationCheckService.isAdmin(user)) {
            return new HashSet<>(courseRepository.findAllActiveForUserAndLearningPathsEnabled(now));
        }
        return new HashSet<>(courseRepository.findAllActiveWhereUserHasAnyRoleAndLearningPathsEnabled(user.getId(), now));
    }
}
