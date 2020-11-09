package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.service.AchievementService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Achievements.
 */
@RestController
@RequestMapping("/api")
public class AchievementResource {

    private final Logger log = LoggerFactory.getLogger(AchievementResource.class);

    private static final String ENTITY_NAME = "achievement";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AchievementRepository achievementRepository;

    private final AchievementService achievementService;

    private final CourseService courseService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    public AchievementResource(AchievementRepository achievementRepository, AchievementService achievementService, CourseService courseService, UserService userService,
            AuthorizationCheckService authCheckService) {
        this.achievementRepository = achievementRepository;
        this.achievementService = achievementService;
        this.courseService = courseService;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /courses/{courseId}/earned-achievements : Get earned achievements for requesting user in given course.
     *
     * @param courseId
     * @return the ResponseEntity with status 200 (OK) and with the found achievements as body
     */
    @GetMapping("/courses/{courseId}/earned-achievements")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Achievement>> getEarnedAchievementsForUserInCourse(@PathVariable Long courseId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get earned achievements for user : {} in course : {}", user.getLogin(), courseId);
        Set<Achievement> achievements = achievementService.findAllByUserIdAndCourseId(user.getId(), courseId);
        achievementService.prepareForClient(achievements);
        return ResponseEntity.ok(achievements);
    }

    /**
     * GET /courses/{courseId}/all-achievements : Get all achievements in given course.
     *
     * @param courseId
     * @return the ResponseEntity with status 200 (OK) and with the found achievements as body
     */
    @GetMapping("/courses/{courseId}/all-achievements")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Achievement>> getAllAchievementsInCourse(@PathVariable Long courseId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all achievements in course : {} by user : {}", courseId, user.getLogin());
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
        Set<Achievement> achievements = achievementService.findAllByCourseId(courseId);
        achievementService.prepareForClient(achievements);
        return ResponseEntity.ok(achievements);
    }

    /**
     * GET /courses/{courseId}/active-achievements : Get active achievements by type in given course.
     *
     * @param courseId
     * @return the ResponseEntity with status 200 (OK) and with the found achievement types as body
     */
    @GetMapping("/courses/{courseId}/active-achievements")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<AchievementType>> getActiveAchievementsInCourse(@PathVariable Long courseId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get active achievements in course : {} by user : {}", courseId, user.getLogin());
        Course course = courseService.findOneWithActiveAchievementTypes(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
        Set<AchievementType> activeAchievements = course.getActiveAchievements();
        return ResponseEntity.ok(activeAchievements);
    }

    /**
     * PUT /courses/{courseId}/achievements : Updates achievements in a course
     *
     * @param courseId
     * @return the ResponseEntity with status 200 (OK) and with the updated achievements as body
     */
    @PutMapping("/courses/{courseId}/achievements")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity configureAchievementsInCourse(@PathVariable Long courseId, @RequestBody Set<Achievement> achievementsToUpdate,
            @RequestBody Set<AchievementType> activeAchievements) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to update achievements in course : {} by user : {}", courseId, user.getLogin());
        Course course = courseService.findOneWithActiveAchievementTypes(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        course.setActiveAchievements(activeAchievements);
        courseService.save(course);

        var updatedAchievements = achievementRepository.saveAll(achievementsToUpdate);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedAchievements.toString())).body(updatedAchievements);
    }
}
