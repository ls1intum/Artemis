package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final QuizQuestionProgressRepository quizQuestionProgressRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public QuizTrainingLeaderboardService(QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository, CourseRepository courseRepository, UserRepository userRepository,
            QuizQuestionProgressRepository quizQuestionProgressRepository, QuizQuestionRepository quizQuestionRepository, AuthorizationCheckService authorizationCheckService) {
        this.quizTrainingLeaderboardRepository = quizTrainingLeaderboardRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.quizQuestionProgressRepository = quizQuestionProgressRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    public long getLeagueForUser(Long userId, Long courseId) {
        QuizTrainingLeaderboard entry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in leaderboard"));
        return entry.getLeagueId();
    }

    public List<LeaderboardEntryDTO> getLeaderboard(long userId, long courseId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        long studentLeagueId;
        if (authorizationCheckService.isStudentInCourse(course, user)) {
            studentLeagueId = getLeagueForUser(userId, courseId);
        }
        else {
            studentLeagueId = 0;
        }
        long leagueId;
        if (studentLeagueId == 0) {
            leagueId = 3;
        }
        else {
            leagueId = studentLeagueId;
        }

        List<QuizTrainingLeaderboard> leaderboardEntries = quizTrainingLeaderboardRepository.findByLeagueIdAndCourseIdOrderByScoreDesc(leagueId, courseId);
        List<LeaderboardEntryDTO> leaderboard = getLeaderboardEntryDTOS(leaderboardEntries, leagueId, studentLeagueId);
        return leaderboard;
    }

    private static List<LeaderboardEntryDTO> getLeaderboardEntryDTOS(List<QuizTrainingLeaderboard> leaderboardEntries, long leagueId, long studentLeagueId) {
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();
        int rank = 1;
        for (QuizTrainingLeaderboard leaderboardEntry : leaderboardEntries) {
            String username = leaderboardEntry.getLeaderboardName() != null ? leaderboardEntry.getLeaderboardName() : leaderboardEntry.getUser().getName();
            leaderboard.add(new LeaderboardEntryDTO(rank++, leagueId, studentLeagueId, username, leaderboardEntry.getTotalScore(), leaderboardEntry.getScore(),
                    leaderboardEntry.getAnsweredCorrectly(), leaderboardEntry.getAnsweredWrong(), leaderboardEntry.getTotalQuestions()));
        }
        return leaderboard;
    }

    public void updateLeaderboardScore(long userId, long courseId, Set<QuizQuestionProgressData> answeredQuestions) {
        int delta = calculateScoreDelta(answeredQuestions);
        int correctAnswers = calculateCorrectAnswers(answeredQuestions);
        int wrongAnswers = calculateWrongAnswers(answeredQuestions);
        int totalAvailableQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(courseId);

        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).orElseGet(() -> {
            QuizTrainingLeaderboard entry = new QuizTrainingLeaderboard();
            entry.setUser(user);
            entry.setCourse(course);
            entry.setLeaderboardName(user.getFirstName()); // This will be adapted to the chosen name of the user later
            entry.setLeagueId(3);
            entry.setScore(0);
            entry.setAnsweredCorrectly(0);
            entry.setAnsweredWrong(0);
            entry.setTotalQuestions(totalAvailableQuestions);
            return entry;
        });

        leaderboardEntry.setScore(leaderboardEntry.getScore() + delta);
        leaderboardEntry.setAnsweredCorrectly(leaderboardEntry.getAnsweredCorrectly() + correctAnswers);
        leaderboardEntry.setAnsweredWrong(leaderboardEntry.getAnsweredWrong() + wrongAnswers);
        leaderboardEntry.setTotalQuestions(totalAvailableQuestions);

        quizTrainingLeaderboardRepository.save(leaderboardEntry);
    }

    private int calculateScoreDelta(Set<QuizQuestionProgressData> answeredQuestions) {
        int delta = 0;
        for (QuizQuestionProgressData data : answeredQuestions) {
            double lastScore = data.getLastScore();
            int box = data.getBox();

            // Preliminary formula for score calculation
            double questionDelta = 2 * lastScore + box * lastScore;

            delta += (int) Math.round(questionDelta);
        }
        return delta;
    }

    private int calculateCorrectAnswers(Set<QuizQuestionProgressData> answeredQuestions) {
        int correctCount = 0;
        for (QuizQuestionProgressData data : answeredQuestions) {
            if (data.getLastScore() >= 1.0) {
                correctCount++;
            }
        }
        return correctCount;
    }

    private int calculateWrongAnswers(Set<QuizQuestionProgressData> answeredQuestions) {
        int wrongCount = 0;
        for (QuizQuestionProgressData data : answeredQuestions) {
            if (data.getLastScore() < 1.0) {
                wrongCount++;
            }
        }
        return wrongCount;
    }

    public void initializeLeaderboard(long userId, long courseId, int totalAvailableQuestions) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        Set<Long> quizQuestionIds = quizQuestionRepository.findQuizQuestionIdsByCourseId(courseId);
        Set<QuizQuestionProgress> progressSet = quizQuestionProgressRepository.findAllByUserIdAndQuizQuestionIdIn(userId, quizQuestionIds);

        Set<QuizQuestionProgressData> progressDataSet = new HashSet<>();
        for (QuizQuestionProgress progress : progressSet) {
            QuizQuestionProgressData progressData = progress.getProgressJson();
            if (progressData != null) {
                progressDataSet.add(progressData);
            }
        }

        int score = calculateScoreDelta(progressDataSet);
        int answeredCorrectly = calculateCorrectAnswers(progressDataSet);
        int answeredWrong = calculateWrongAnswers(progressDataSet);

        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).orElseGet(() -> {
            QuizTrainingLeaderboard entry = new QuizTrainingLeaderboard();
            entry.setUser(user);
            entry.setCourse(course);
            entry.setLeaderboardName(user.getFirstName()); // This will be adapted to the chosen name of the user later
            entry.setLeagueId(3);
            entry.setScore(score);
            entry.setAnsweredCorrectly(answeredCorrectly);
            entry.setAnsweredWrong(answeredWrong);
            entry.setTotalQuestions(totalAvailableQuestions);
            return entry;
        });
        quizTrainingLeaderboardRepository.save(leaderboardEntry);
    }

    public void initializeLeaderboardsForCourse(long courseId, int totalAvailableQuestions) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        Set<User> users = userRepository.getUsersInCourse(course);

        for (User user : users) {
            initializeLeaderboard(user.getId(), courseId, totalAvailableQuestions);
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void weeklyLeaderboardRebuild() {
        List<Course> allCourses = courseRepository.findAll();
        for (Course course : allCourses) {
            int totalQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(course.getId());
            initializeLeaderboardsForCourse(course.getId(), totalQuestions);
        }
    }
}
