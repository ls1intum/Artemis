package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizTrainingLeaderboardRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizTrainingLeaderboardService {

    private final QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public QuizTrainingLeaderboardService(QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository, CourseRepository courseRepository, UserRepository userRepository) {
        this.quizTrainingLeaderboardRepository = quizTrainingLeaderboardRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    public long getLeagueForUser(Long userId, Long courseId) {
        QuizTrainingLeaderboard entry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in leaderboard"));
        return entry.getLeagueId();
    }

    public List<LeaderboardEntryDTO> getLeaderboard(long userId, long courseId) {
        long leagueId = getLeagueForUser(userId, courseId);
        List<QuizTrainingLeaderboard> leaderboardEntries = quizTrainingLeaderboardRepository.findByLeagueIdAndCourseIdOrderByScoreDesc(leagueId, courseId);
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();
        int rank = 1;
        for (QuizTrainingLeaderboard leaderboardEntry : leaderboardEntries) {
            String username = leaderboardEntry.getUser() != null ? leaderboardEntry.getUser().getName() : "Unknown User";
            leaderboard.add(new LeaderboardEntryDTO(rank++, leagueId, username, leaderboardEntry.getScore()));
        }
        return leaderboard;
    }

    public void updateLeaderboardScore(long userId, long courseId, Map<QuizQuestion, QuizQuestionProgressData> answeredQuestions) {
        int delta = calculateScoreDelta(answeredQuestions);
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).orElseGet(() -> {
            QuizTrainingLeaderboard entry = new QuizTrainingLeaderboard();
            entry.setUser(user);
            entry.setCourse(course);
            entry.setLeagueId(3); // get leagueId as needed
            entry.setScore(0);
            return entry;
        });

        leaderboardEntry.setScore(leaderboardEntry.getScore() + delta);
        quizTrainingLeaderboardRepository.save(leaderboardEntry);
    }

    private int calculateScoreDelta(Map<QuizQuestion, QuizQuestionProgressData> answeredQuestions) {
        int delta = 0;
        for (QuizQuestionProgressData data : answeredQuestions.values()) {
            double lastScore = data.getLastScore();
            int repetition = data.getRepetition();
            int box = data.getBox();
            int sessionCount = data.getSessionCount();
            double easinessFactor = data.getEasinessFactor();

            // Example progress-based formula (adjust weights as you like)
            double questionDelta = (lastScore * 10) + (repetition * 5) + (box * 2) + sessionCount + (Math.max(0, 2.5 - easinessFactor) * 4);

            delta += (int) Math.round(questionDelta);
        }
        return delta;
    }
}
