package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AchievementRepository;
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

    private final AchievementRepository achievementRepository;

    private final UserService userService;

    public AchievementResource(AchievementService achievementService, AchievementRepository achievementRepository, UserService userService) {
        this.achievementService = achievementService;
        this.achievementRepository = achievementRepository;
        this.userService = userService;
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
        achievementService.prepareForClient(achievements);
        return ResponseEntity.ok(achievements);
    }
}
