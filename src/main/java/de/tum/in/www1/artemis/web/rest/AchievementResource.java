package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;
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
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.service.AchievementService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Achievements.
 */
@RestController
@RequestMapping("/api")
public class AchievementResource {

    private static final String ENTITY_NAME = "achievement";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(AchievementResource.class);

    private final AuthorizationCheckService authCheckService;

    private final AchievementService achievementService;

    private final AchievementRepository achievementRepository;

    private final UserService userService;

    public AchievementResource(AuthorizationCheckService authCheckService, AchievementService achievementService, AchievementRepository achievementRepository,
            UserService userService) {
        this.authCheckService = authCheckService;
        this.achievementService = achievementService;
        this.achievementRepository = achievementRepository;
        this.userService = userService;
    }

    /**
     * GET /achievements : Get all achievements for user.
     *
     * @return the ResponseEntity with status 200 (OK) and with the found achievements as body
     */
    @GetMapping("/achievements")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Achievement>> getAchievementsForUser() {
        User user = userService.getUser();
        log.debug("REST request to get achievements for user : {}", user.getLogin());
        Set<Achievement> achievements = achievementService.findAllByUserId(user.getId());
        return ResponseEntity.ok(achievements);
    }

    /**
     * GET /courses/{courseId}/achievements : Get all achievements for course.
     *
     * @param courseId the course to which the achievements belong to
     * @return the ResponseEntity with status 200 (OK) and with the found achievements as body
     */
    @GetMapping("/courses/{courseId}/achievements")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Achievement>> getAchievementsForUserInCourse(@PathVariable Long courseId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get achievements for user : {} in course : {}", user.getLogin(), courseId);
        Set<Achievement> achievements = achievementRepository.findAllByUserIdAndCourseId(user.getId(), courseId);

        return ResponseEntity.ok(achievements);
    }

    /**
     * PUT /achievements : Updates an existing achievement.
     *
     * @param achievement the achievement to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated achievement, or with status 500
     *         (Internal Server Error) if the achievement couldn't be updated
     */
    @PutMapping("/achievements")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Achievement> updateAchievement(@RequestBody Achievement achievement) {
        log.debug("REST request to update Achievement : {}", achievement);

        if (achievement.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Course course = achievement.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        Achievement savedAchievement = achievementRepository.save(achievement);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, savedAchievement.getId().toString())).body(savedAchievement);
    }

    /**
     * DELETE /achievements/:achievementId : delete the "id" achievement.
     *
     * @param achievementId the id of the achievement to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/achievements/{achievementId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteAchievement(@PathVariable long achievementId) {
        log.info("REST request to delete Achievement : {}", achievementId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<Achievement> optionalAchievement = achievementService.findById(achievementId);
        if (optionalAchievement.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Achievement achievement = optionalAchievement.get();
        Course course = achievement.getCourse();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        log.info("User " + user.getLogin() + " has requested to delete the achievement {}", achievement.getId());
        achievementService.delete(achievement);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, achievement.getId().toString())).build();
    }

}
