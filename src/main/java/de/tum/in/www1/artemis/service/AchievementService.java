package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class AchievementService {

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    public AchievementService(ParticipationService participationService, UserRepository userRepository) {
        this.participationService = participationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public User assignPointBasedAchievmentIfEarned(Result result) {
        var score = result.getScore();
        // TODO: add actually required score for achievements
        var gold = 0.9;
        var silver = 0.8;
        var bronze = 0.7;

        // TODO: get respective achievement
        var achievement = new Achievement();

        var optionalUser = participationService.findOneStudentParticipation(result.getParticipation().getId()).getStudent();

        if (optionalUser.isPresent()) {
            var user = optionalUser.get();

            if (score >= gold && !user.getAchievements().contains(achievement)) {
                user.addAchievement(achievement);
                return userRepository.save(user);
            }
            else if (score >= silver && !user.getAchievements().contains(achievement)) {
                user.addAchievement(achievement);
                return userRepository.save(user);
            }
            else if (score >= bronze && !user.getAchievements().contains(achievement)) {
                user.addAchievement(achievement);
                return userRepository.save(user);
            }
        }

        return null;
    }
}
