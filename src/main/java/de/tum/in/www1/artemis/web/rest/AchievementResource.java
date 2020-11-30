package de.tum.in.www1.artemis.web.rest;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
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
        // IGNORE THE FOLLOWING CHANGES AS THEY ARE ONLY FOR TESTING PURPOSES
        // Set<Achievement> achievements = achievementService.findAllByUserIdAndCourseId(user.getId(), courseId);
        // achievementService.prepareForClient(achievements);
        var achievements = new HashSet<Achievement>();
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "award", AchievementRank.GOLD, AchievementType.POINT, 100L, null, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "award", AchievementRank.SILVER, AchievementType.POINT, 80L, null, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "award", AchievementRank.BRONZE, AchievementType.POINT, 60L, null, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "award", AchievementRank.UNRANKED, AchievementType.POINT, 50L, null, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "calendar-check", AchievementRank.GOLD, AchievementType.TIME, 1L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "calendar-check", AchievementRank.SILVER, AchievementType.TIME, 2L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "calendar-check", AchievementRank.BRONZE, AchievementType.TIME, 3L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "calendar-check", AchievementRank.UNRANKED, AchievementType.TIME, 4L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "tasks", AchievementRank.GOLD, AchievementType.PROGRESS, 10L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "tasks", AchievementRank.SILVER, AchievementType.PROGRESS, 8L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "tasks", AchievementRank.BRONZE, AchievementType.PROGRESS, 5L, 50L, null, null));
        achievements
                .add(new Achievement("Lorem ipsum", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua",
                        "tasks", AchievementRank.UNRANKED, AchievementType.PROGRESS, 1L, 50L, null, null));
        return ResponseEntity.ok(achievements);
    }
}
