package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizTrainingLeaderboardRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizTrainingLeaderboardService {

    private final QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final QuizQuestionProgressRepository quizQuestionProgressRepository;

    private static final int BRONZE_LEAGUE = 5;

    private static final int SILVER_LEAGUE = 4;

    private static final int GOLD_LEAGUE = 3;

    private static final int DIAMOND_LEAGUE = 2;

    private static final int MASTER_LEAGUE = 1;

    private static final int NO_LEAGUE = 0;

    public QuizTrainingLeaderboardService(QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository, CourseRepository courseRepository, UserRepository userRepository,
            QuizQuestionRepository quizQuestionRepository, AuthorizationCheckService authorizationCheckService, QuizQuestionProgressRepository quizQuestionProgressRepository) {
        this.quizTrainingLeaderboardRepository = quizTrainingLeaderboardRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.quizQuestionProgressRepository = quizQuestionProgressRepository;
    }

    /**
     * Retrieves the leaderboard entries for a given user and course.
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @return a list of leaderboard entry DTOs
     */
    public List<LeaderboardEntryDTO> getLeaderboard(long userId, long courseId) {
        User user = userRepository.findByIdElseThrow(userId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        long totalQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(courseId);
        int studentLeague;
        if (authorizationCheckService.isStudentInCourse(course, user)) {
            studentLeague = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).map(QuizTrainingLeaderboard::getLeague).orElse(BRONZE_LEAGUE);
        }
        else {
            studentLeague = NO_LEAGUE;
        }
        int selectedLeague;
        if (studentLeague == 0) {
            selectedLeague = BRONZE_LEAGUE;
        }
        else {
            selectedLeague = studentLeague;
        }

        List<QuizTrainingLeaderboard> leaderboardEntries = quizTrainingLeaderboardRepository.findByLeagueAndCourseIdOrderByScoreDescUserAscId(selectedLeague, courseId);
        return getLeaderboardEntryDTOS(leaderboardEntries, selectedLeague, totalQuestions);
    }

    /**
     * Converts a list of leaderboard entities to DTOs, including rank and league information.
     *
     * @param leaderboardEntries the list of leaderboard entities
     * @param selectedLeague     the league ID to use for the entries
     * @return a list of leaderboard entry DTOs
     */
    private static List<LeaderboardEntryDTO> getLeaderboardEntryDTOS(List<QuizTrainingLeaderboard> leaderboardEntries, int selectedLeague, long totalQuestions) {
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();
        int rank = 1;
        for (QuizTrainingLeaderboard leaderboardEntry : leaderboardEntries) {
            String username = leaderboardEntry.getLeaderboardName() != null ? leaderboardEntry.getLeaderboardName() : leaderboardEntry.getUser().getName();
            leaderboard.add(new LeaderboardEntryDTO(rank++, selectedLeague, leaderboardEntry.getUser().getId(), leaderboardEntry.getUser().getName(),
                    leaderboardEntry.getUser().getImageUrl(), username, leaderboardEntry.getScore(), leaderboardEntry.getAnsweredCorrectly(), leaderboardEntry.getAnsweredWrong(),
                    totalQuestions, leaderboardEntry.getDueDate(), leaderboardEntry.getStreak()));
        }
        return leaderboard;
    }

    public void setInitialLeaderboardEntry(long userId, long courseId, boolean shownInLeaderboard, String leaderboardName) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.findByIdElseThrow(userId);
        QuizTrainingLeaderboard leaderboardEntry = new QuizTrainingLeaderboard();
        leaderboardEntry.setUser(user);
        leaderboardEntry.setCourse(course);
        leaderboardEntry.setLeague(BRONZE_LEAGUE);
        leaderboardEntry.setScore(0);
        leaderboardEntry.setAnsweredCorrectly(0);
        leaderboardEntry.setAnsweredWrong(0);
        leaderboardEntry.setDueDate(ZonedDateTime.now());
        leaderboardEntry.setStreak(0);
        leaderboardEntry.setShowInLeaderboard(shownInLeaderboard);
        leaderboardEntry.setLeaderboardName(leaderboardName);
        quizTrainingLeaderboardRepository.save(leaderboardEntry);
    }

    /**
     * Updates the leaderboard score for a user in a course based on answered questions.
     *
     * @param userId           the ID of the user
     * @param courseId         the ID of the course
     * @param answeredQuestion the set of answered question progress data
     * @throws IllegalArgumentException if the user or course is not found
     */
    public void updateLeaderboardScore(long userId, long courseId, QuizQuestionProgressData answeredQuestion) {
        int delta = calculateScoreDelta(answeredQuestion);
        int correctAnswers = answeredQuestion.getLastScore() == 1.0 ? 1 : 0;
        int wrongAnswers = answeredQuestion.getLastScore() < 1.0 ? 1 : 0;

        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).orElseThrow();
        ZonedDateTime dueDate = findLatestDueDate(userId, courseId);
        int score = leaderboardEntry.getScore() + delta;
        int league = calculateLeague(score);

        quizTrainingLeaderboardRepository.updateLeaderboardEntry(userId, courseId, score, correctAnswers, wrongAnswers, league, dueDate);
    }

    private int calculateLeague(int score) {
        if (score < 100) {
            return BRONZE_LEAGUE;
        }
        else if (score < 200) {
            return SILVER_LEAGUE;
        }
        else if (score < 300) {
            return GOLD_LEAGUE;
        }
        else if (score < 400) {
            return DIAMOND_LEAGUE;
        }
        else {
            return MASTER_LEAGUE;
        }
    }

    /**
     * Finds the earliest due date from a set of quiz question progress data.
     * If no due dates are available, returns the current time.
     *
     * @return the earliest due date found or the current time if none exists
     */
    private ZonedDateTime findLatestDueDate(long userId, long courseId) {
        return quizQuestionProgressRepository.findAllByUserIdAndCourseId(userId, courseId).stream().map(QuizQuestionProgress::getProgressJson)
                .map(QuizQuestionProgressData::getDueDate).filter(Objects::nonNull).min(ZonedDateTime::compareTo).orElse(ZonedDateTime.now());
    }

    /**
     * Calculates the score delta based on the answered question.
     *
     * @param answeredQuestion the answered question progress data
     * @return the calculated score delta
     */
    private int calculateScoreDelta(QuizQuestionProgressData answeredQuestion) {
        int delta = 0;
        double lastScore = answeredQuestion.getLastScore();
        int box = answeredQuestion.getBox();

        // Preliminary formula for score calculation
        double questionDelta = 2 * lastScore + box * lastScore;

        delta += (int) Math.round(questionDelta);
        return delta;
    }

    public void updateLeaderboardName(long userId, String newName) {
        quizTrainingLeaderboardRepository.updateLeaderboardName(userId, newName);
    }

    public void updateShownInLeaderboard(long userId, boolean shownInLeaderboard) {
        quizTrainingLeaderboardRepository.updateShownInLeaderboard(userId, shownInLeaderboard);
    }
}
