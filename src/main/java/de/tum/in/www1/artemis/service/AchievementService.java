package de.tum.in.www1.artemis.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class AchievementService {

    private final PointBasedAchievementService pointBasedAchievementService;

    private final TimeBasedAchievementService timeBasedAchievementService;

    private final ProgressBasedAchievementService progressBasedAchievementService;

    private final AchievementRepository achievementRepository;

    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(AchievementService.class);

    public AchievementService(AchievementRepository achievementRepository, UserRepository userRepository, PointBasedAchievementService pointBasedAchievementService,
            TimeBasedAchievementService timeBasedAchievementService, ProgressBasedAchievementService progressBasedAchievementService) {
        this.achievementRepository = achievementRepository;
        this.pointBasedAchievementService = pointBasedAchievementService;
        this.timeBasedAchievementService = timeBasedAchievementService;
        this.progressBasedAchievementService = progressBasedAchievementService;
        this.userRepository = userRepository;
    }

    /**
     * Finds all achievements for a user in a given course and returns them as a set
     * @param userId
     * @param courseId
     * @return set of achievements
     */
    public Set<Achievement> findAllByUserIdAndCourseId(Long userId, Long courseId) {
        return achievementRepository.findAllByUserIdAndCourseId(userId, courseId);
    }

    /**
     * Deletes all achievements that belong to the course with the given courseId
     * Used when a course is deleted or when achievements are disabled again for a course
     * @param courseId
     */
    public void deleteByCourseId(Long courseId) {
        Set<Achievement> achievements = achievementRepository.findAllByCourseId(courseId);
        for (Achievement achievement : achievements) {
            removeFromUsers(achievement);
        }
        achievementRepository.deleteAll(achievements);
    }

    /**
     * Removes an achievement from all users
     * @param achievement achievement to be deleted
     */
    public void removeFromUsers(Achievement achievement) {
        var users = achievement.getUsers();
        for (User user : users) {
            user.removeAchievement(achievement);
        }
        userRepository.saveAll(users);
    }

    /**
     * Generates achievements for a course
     * Used when course is updated or created and achievements are enabled for course
     * @param course
     */
    public void generateForCourse(Course course) {
        progressBasedAchievementService.generateAchievements(course);
        pointBasedAchievementService.generateAchievements(course);
        timeBasedAchievementService.generateAchievements(course);
    }

    /**
     * Prepares the given set of achievements to be sent to the client by removing exercise, course and users
     * @param achievements
     */
    public void prepareForClient(Set<Achievement> achievements) {
        for (Achievement achievement : achievements) {
            achievement.setExercise(null);
            achievement.setCourse(null);
            achievement.setUsers(null);
        }
    }

    /**
     * Checks the result if it earned any achievements
     * @param result
     */
    @Transactional
    public void checkForAchievements(Result result) {
        log.debug("checkForAchievements was invoked");
        var participation = result.getParticipation();
        if (participation == null || participation.getId() == null || !(participation instanceof StudentParticipation)) {
            return;
        }
        log.debug("first check passed");
        var studentParticipation = (StudentParticipation) participation;
        var exercise = studentParticipation.getExercise();
        if (exercise == null || exercise.getExerciseGroup() != null) {
            return;
        }
        log.debug("second check passed");
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null || !course.getAchievementsEnabled()) {
            return;
        }
        log.debug("third check passed");
        var optionalUser = studentParticipation.getStudent();
        if (optionalUser.isEmpty()) {
            return;
        }
        log.debug("fourth check passed");
        // var user = userRepository.findOneWithEagerAchievements(optionalUser.get().getId());
        // if (user == null) {
        // return;
        // }
        var achievements = achievementRepository.findAllByUserId(optionalUser.get().getId());
        if (achievements == null) {
            return;
        }
        var user = optionalUser.get();
        user.setAchievements(achievements);

        log.debug("all checks passed");

        var pointBasedAchievements = achievementRepository.findAllForRewardedTypeInCourse(course.getId(), AchievementType.POINT);
        var pointRank = pointBasedAchievementService.checkForAchievement(result, pointBasedAchievements);

        log.debug("reward pointBased invoked with {} achievements and rank : {}", pointBasedAchievements.size(), pointRank);
        rewardAchievement(pointBasedAchievements, pointRank, user);

        var timeBasedAchievements = achievementRepository.findAllForRewardedTypeInCourse(course.getId(), AchievementType.TIME);
        var timeRank = timeBasedAchievementService.checkForAchievement(result, timeBasedAchievements);

        log.debug("reward timeBased invoked with {} achievements and rank : {}", timeBasedAchievements.size(), timeRank);
        rewardAchievement(timeBasedAchievements, timeRank, user);

        var progressBasedAchievements = achievementRepository.findAllForRewardedTypeInCourse(course.getId(), AchievementType.PROGRESS);
        var progressRank = progressBasedAchievementService.checkForAchievement(course, user, progressBasedAchievements);

        log.debug("reward progressBased invoked with {} achievements and rank : {}", progressBasedAchievements.size(), progressRank);
        rewardAchievement(progressBasedAchievements, progressRank, user);
    }

    /**
     * Rewards or replaces an achievement
     */
    private void rewardAchievement(Set<Achievement> achievements, AchievementRank rank, User user) {
        if (rank == null) {
            return;
        }

        var optionalAchievement = achievements.stream().filter(a -> a.getRank().equals(rank)).findAny();
        if (optionalAchievement.isEmpty()) {
            return;
        }

        var achievement = optionalAchievement.get();
        if (achievement.getUsers().contains(user)) {
            return;
        }

        var optionalAchievementsOfHigherRank = achievements.stream().filter(a -> a.getRank().ordinal() > rank.ordinal()).collect(Collectors.toSet());
        for (Achievement a : optionalAchievementsOfHigherRank) {
            if (a.getUsers().contains(user)) {
                return;
            }
        }

        var optionalAchievementsOfLowerRank = achievements.stream().filter(a -> a.getRank().ordinal() < rank.ordinal()).collect(Collectors.toSet());
        for (Achievement a : optionalAchievementsOfLowerRank) {
            if (a.getUsers().contains(user)) {
                user.removeAchievement(a);
            }
        }

        user.addAchievement(achievement);
        userRepository.save(user);
        log.debug("user was rewarded with achievement : {}", achievement);
    }
}
