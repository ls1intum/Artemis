package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * GET /achievements : Get all achievements for user.
     *
     * @return the ResponseEntity with status 200 (OK) and with the found achievements as body
     */
    @GetMapping("/achievements")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Achievement>> getAchievementsForUser() {
        User user = userService.getUser();
        log.debug("REST request to get achievements for user : {}", user.getLogin());
        Set<Achievement> achievements = achievementService.findAllForUser(user.getId());
        return ResponseEntity.ok(achievements);
    }

}
