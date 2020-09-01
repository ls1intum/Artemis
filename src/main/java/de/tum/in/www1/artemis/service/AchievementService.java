package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class AchievementService {

    private final AchievementRepository achievementRepository;

    private final UserRepository userRepository;

    public AchievementService(AchievementRepository achievementRepository, UserRepository userRepository) {
        this.achievementRepository = achievementRepository;
        this.userRepository = userRepository;
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

    public Achievement save(Achievement achievement) {
        return achievementRepository.save(achievement);
    }

    public Achievement create(String title, String description, String icon, AchievementRank rank, Course course) {
        Achievement achievement = new Achievement();
        achievement.setTitle(title);
        achievement.setDescription(description);
        achievement.setIcon(icon);
        achievement.setRank(rank);
        achievement.setCourse(course);
        return achievementRepository.save(achievement);
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
}
