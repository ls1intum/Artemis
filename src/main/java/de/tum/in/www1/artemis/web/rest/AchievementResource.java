package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.AchievementService;
import de.tum.in.www1.artemis.service.UserService;

/**
 * REST controller for managing Achievements.
 */
@RestController
@RequestMapping("/api")
public class AchievementResource {

    private final Logger log = LoggerFactory.getLogger(AchievementResource.class);

    private final AchievementService achievementService;

    private final UserService userService;

    public AchievementResource(AchievementService achievementService, UserService userService) {
        this.achievementService = achievementService;
        this.userService = userService;
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
}
