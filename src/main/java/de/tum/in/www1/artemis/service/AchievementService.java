package de.tum.in.www1.artemis.service;

import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class AchievementService {

    private final PointBasedAchievementService pointBasedAchievementService;

    private final TimeBasedAchievementService timeBasedAchievementService;

    private final ProgressBasedAchievementService progressBasedAchievementService;

    private final ParticipationService participationService;

    private final AchievementRepository achievementRepository;

    private final UserRepository userRepository;

    public AchievementService(AchievementRepository achievementRepository, UserRepository userRepository, PointBasedAchievementService pointBasedAchievementService,
            TimeBasedAchievementService timeBasedAchievementService, ProgressBasedAchievementService progressBasedAchievementService, ParticipationService participationService) {
        this.achievementRepository = achievementRepository;
        this.pointBasedAchievementService = pointBasedAchievementService;
        this.timeBasedAchievementService = timeBasedAchievementService;
        this.progressBasedAchievementService = progressBasedAchievementService;
        this.userRepository = userRepository;
        this.participationService = participationService;
    }

    /**
     * Finds all achievements for a user in a given course and returns them as a set
     * @param userId
     * @param courseId
     */
    public Set<Achievement> findAllByUserIdAndCourseId(Long userId, Long courseId) {
        return achievementRepository.findAllByUserIdAndCourseId(userId, courseId);
    }

    /**
     * Deletes all achievements that belong to the course with the given courseId
     * Used when a course is deleted or when achievements are disabled again for a course
     * @param courseId
     */
    @Transactional
    public void deleteByCourseId(Long courseId) {
        Set<Achievement> achievements = achievementRepository.findAllByCourseId(courseId);
        for (Achievement achievement : achievements) {
            removeFromUsers(achievement);
        }
        achievementRepository.deleteAll(achievements);
    }

    /**
     * Deletes all achievements that belong to the exercise with the given exerciseId
     * Used when an exercise is deleted
     * @param exerciseId
     */
    @Transactional
    public void deleteByExerciseId(Long exerciseId) {
        Set<Achievement> achievements = achievementRepository.findAllByExerciseId(exerciseId);
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
    }

    /**
     * Generates achievements for an exercise
     * Used when exercise is created and achievements are enabled for corresponding course
     * @param exercise
     */
    public void generateForExercise(Exercise exercise) {
        pointBasedAchievementService.generateAchievements(exercise);
        timeBasedAchievementService.generateAchievements(exercise);
    }

    /**
     * Checks the result if it earned any achievements
     * @param result
     */
    @Transactional
    public void checkForAchievements(Result result) {
        var course = result.getParticipation().getExercise().getCourseViaExerciseGroupOrCourseMember();
        if (course.getHasAchievements() == null || !course.getHasAchievements()) {
            return;
        }
        var exercise = result.getParticipation().getExercise();
        var optionalUser = participationService.findOneStudentParticipation(result.getParticipation().getId()).getStudent();
        if (!optionalUser.isPresent()) {
            return;
        }
        var user = optionalUser.get();

        var pointRank = pointBasedAchievementService.checkForAchievement(result);
        rewardAchievement(course, exercise, AchievementType.POINT, pointRank, user);

        var timeRank = timeBasedAchievementService.checkForAchievement(result);
        rewardAchievement(course, exercise, AchievementType.TIME, timeRank, user);

        var progressRank = progressBasedAchievementService.checkForAchievement(course, user);
        rewardAchievement(course, null, AchievementType.PROGRESS, progressRank, user);
    }

    /**
     * Rewards or replaces an achievement
     */
    public void rewardAchievement(Course course, Exercise exercise, AchievementType type, AchievementRank rank, User user) {
        Set<Achievement> achievements;
        Achievement achievement;

        if (rank == null) {
            return;
        }

        if (exercise == null) {
            achievements = achievementRepository.findAllForRewardedTypeInCourse(course.getId(), type);
        }
        else {
            achievements = achievementRepository.findAllForRewardedTypeInExercise(course.getId(), exercise.getId(), type);
        }

        var optionalAchievement = achievements.stream().filter(a -> a.getRank().equals(rank)).findAny();
        if (!optionalAchievement.isPresent()) {
            return;
        }

        achievement = optionalAchievement.get();
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
            if (a.getUsers().contains(user) && a.getExercise() != null) {
                user.removeAchievement(a);
            }
        }

        user.addAchievement(achievement);
        userRepository.save(user);
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
}
