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

    /**
     * Returns the league ID for a given user and course.
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @return the league ID of the user in the course
     * @throws IllegalArgumentException if the user is not found in the leaderboard
     */
    public long getLeagueForUser(Long userId, Long courseId) {
        QuizTrainingLeaderboard entry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in leaderboard"));
        return entry.getLeagueId();
    }

    /**
     * Retrieves the leaderboard entries for a given user and course.
     *
     * @param userId   the ID of the user
     * @param courseId the ID of the course
     * @return a list of leaderboard entry DTOs
     * @throws IllegalArgumentException if the user or course is not found
     */
    public List<LeaderboardEntryDTO> getLeaderboard(long userId, long courseId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        long studentLeagueId;
        if (authorizationCheckService.isStudentInCourse(course, user)) {
            studentLeagueId = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).map(QuizTrainingLeaderboard::getLeagueId).orElse(3L);
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

        List<QuizTrainingLeaderboard> leaderboardEntries = quizTrainingLeaderboardRepository.findByLeagueIdAndCourseIdOrderByScoreDescTotalScoreDescUserAscId(leagueId, courseId);
        List<LeaderboardEntryDTO> leaderboard = getLeaderboardEntryDTOS(leaderboardEntries, leagueId, studentLeagueId);
        return leaderboard;
    }

    /**
     * Converts a list of leaderboard entities to DTOs, including rank and league information.
     *
     * @param leaderboardEntries the list of leaderboard entities
     * @param leagueId           the league ID to use for the entries
     * @param studentLeagueId    the league ID of the student
     * @return a list of leaderboard entry DTOs
     */
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

    /**
     * Updates the leaderboard score for a user in a course based on answered questions.
     *
     * @param userId            the ID of the user
     * @param courseId          the ID of the course
     * @param answeredQuestions the set of answered question progress data
     * @throws IllegalArgumentException if the user or course is not found
     */
    public void updateLeaderboardScore(long userId, long courseId, Set<QuizQuestionProgressData> answeredQuestions) {
        int delta = calculateScoreDelta(answeredQuestions);
        int correctAnswers = calculateCorrectAnswers(answeredQuestions);
        int wrongAnswers = calculateWrongAnswers(answeredQuestions);
        long totalAvailableQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(courseId);

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

    /**
     * Calculates the score delta based on the answered questions.
     *
     * @param answeredQuestions the set of answered question progress data
     * @return the calculated score delta
     */
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

    /**
     * Calculates the number of correctly answered questions.
     *
     * @param answeredQuestions the set of answered question progress data
     * @return the number of correct answers
     */
    private int calculateCorrectAnswers(Set<QuizQuestionProgressData> answeredQuestions) {
        int correctCount = 0;
        for (QuizQuestionProgressData data : answeredQuestions) {
            if (data.getLastScore() >= 1.0) {
                correctCount++;
            }
        }
        return correctCount;
    }

    /**
     * Calculates the number of incorrectly answered questions.
     *
     * @param answeredQuestions the set of answered question progress data
     * @return the number of wrong answers
     */
    private int calculateWrongAnswers(Set<QuizQuestionProgressData> answeredQuestions) {
        int wrongCount = 0;
        for (QuizQuestionProgressData data : answeredQuestions) {
            if (data.getLastScore() < 1.0) {
                wrongCount++;
            }
        }
        return wrongCount;
    }

    /**
     * Initializes the leaderboard entry for a user in a course.
     *
     * @param userId                  the ID of the user
     * @param courseId                the ID of the course
     * @param totalAvailableQuestions the total number of available questions in the course
     * @throws IllegalArgumentException if the user or course is not found
     */
    public void initializeLeaderboard(long userId, long courseId, long totalAvailableQuestions) {
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

    /**
     * Initializes leaderboard entries for all users in a course.
     *
     * @param courseId                the ID of the course
     * @param totalAvailableQuestions the total number of available questions in the course
     * @throws IllegalArgumentException if the course is not found
     */
    public void initializeLeaderboardsForCourse(long courseId, long totalAvailableQuestions) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        Set<User> users = userRepository.getUsersInCourse(course);

        for (User user : users) {
            initializeLeaderboard(user.getId(), courseId, totalAvailableQuestions);
        }
    }

    /**
     * Scheduled task that rebuilds all leaderboards for all courses daily at 2 am.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void weeklyLeaderboardRebuild() {
        List<Course> allCourses = courseRepository.findAll();
        for (Course course : allCourses) {
            long totalQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(course.getId());
            initializeLeaderboardsForCourse(course.getId(), totalQuestions);
        }
    }
}
