package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.repository.AchievementRepository;

@Service
public class AchievementService {

    private final AchievementRepository achievementRepository;

    public AchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    public Set<Achievement> findAllForCourse(Long courseId) {
        return achievementRepository.getAllByCourseId(courseId);
    }

    public Set<Achievement> findAllForUser(Long userId) {
        return achievementRepository.getAllByUserId(userId);
    }
}
