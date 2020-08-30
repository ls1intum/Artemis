package de.tum.in.www1.artemis.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.User;
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

    @Transactional
    public Set<Achievement> findAllForCourse(Long courseId, Long userId) {
        var achievements = achievementRepository.getAllByCourseId(courseId);
        return hideUsersInAchievements(achievements, userId);
    }

    @Transactional
    public Set<Achievement> findAllForUser(Long userId) {
        var achievements = achievementRepository.getAllByUserId(userId);
        return hideUsersInAchievements(achievements, userId);
    }

    public void delete(Achievement achievement) {
        for (User user : achievement.getUsers()) {
            user.removeAchievement(achievement);
            userRepository.save(user);
        }
        achievementRepository.delete(achievement);
    }

    private Set<Achievement> hideUsersInAchievements(Set<Achievement> achievements, Long userId) {
        for (Achievement achievement : achievements) {
            achievement.getUsers().removeIf(userToRemove -> !userToRemove.getId().equals(userId));
        }
        return achievements;
    }
}
