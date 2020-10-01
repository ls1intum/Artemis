package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Optional<Achievement> findById(Long achievementId) {
        return achievementRepository.findById(achievementId);
    }

    public List<Achievement> findAll() {
        return achievementRepository.findAll();
    }

    public Set<Achievement> findAllByCourseId(Long courseId) {
        return achievementRepository.findAllByCourseId(courseId);
    }

    public Set<Achievement> findAllByExerciseId(Long exerciseId) {
        return achievementRepository.findAllByExerciseId(exerciseId);
    }

    public Set<Achievement> findAllByUserId(Long userId) {
        return achievementRepository.findAllByUserId(userId);
    }

    public void deleteAchievementsForCourse(Course course) {
        achievementRepository.deleteByCourse_Id(course.getId());
    }

    /**
     * Deletes an achievement by also removing it from all users
     * @param achievement achievement to be deleted
     */
    public void delete(Achievement achievement) {
        var users = userRepository.findAllByAchievementId(achievement.getId());
        achievement.setUsers(users);
        for (User user : users) {
            user.removeAchievement(achievement);
            userRepository.save(user);
        }
        achievementRepository.delete(achievement);
    }

    public void generateForCourse(Course course) {
        progressBasedAchievementService.generateAchievements(course);
    }

    public void generateForExercise(Exercise exercise) {
        pointBasedAchievementService.generateAchievements(exercise);
        timeBasedAchievementService.generateAchievements(exercise);
    }

    public void checkForAchievements(Result result) {
        var course = result.getParticipation().getExercise().getCourseViaExerciseGroupOrCourseMember();
        if (!course.getHasAchievements()) {
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
        rewardAchievement(course, exercise, AchievementType.PROGRESS, progressRank, user);
    }

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

    public void prepareForClient(Set<Achievement> achievements) {
        for (Achievement achievement : achievements) {
            achievement.setExercise(null);
            achievement.setCourse(null);
            achievement.setUsers(null);
        }
    }
}
