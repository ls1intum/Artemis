package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private final QuizQuestionProgressRepository quizQuestionProgressRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private static final int BRONZE_LEAGUE = 3;

    private static final int NO_LEAGUE = 0;

    private record AnswerCounts(int correct, int wrong) {
    }

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
        int studentLeague;
        if (authorizationCheckService.isStudentInCourse(course, user)) {
            studentLeague = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).map(QuizTrainingLeaderboard::getLeague).orElse(BRONZE_LEAGUE);
        }
        else {
            studentLeague = NO_LEAGUE;
        }
        int league;
        if (studentLeague == 0) {
            league = BRONZE_LEAGUE;
        }
        else {
            league = studentLeague;
        }

        List<QuizTrainingLeaderboard> leaderboardEntries = quizTrainingLeaderboardRepository.findByLeagueAndCourseIdOrderByScoreDescUserAscId(league, courseId);
        List<LeaderboardEntryDTO> leaderboard = getLeaderboardEntryDTOS(leaderboardEntries, league, studentLeague);
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
    private static List<LeaderboardEntryDTO> getLeaderboardEntryDTOS(List<QuizTrainingLeaderboard> leaderboardEntries, int leagueId, int studentLeagueId) {
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();
        int rank = 1;
        for (QuizTrainingLeaderboard leaderboardEntry : leaderboardEntries) {
            String username = leaderboardEntry.getLeaderboardName() != null ? leaderboardEntry.getLeaderboardName() : leaderboardEntry.getUser().getName();
            leaderboard.add(new LeaderboardEntryDTO(rank++, leagueId, studentLeagueId, leaderboardEntry.getUser().getId(), leaderboardEntry.getUser().getName(),
                    leaderboardEntry.getUser().getImageUrl(), username, leaderboardEntry.getScore(), leaderboardEntry.getAnsweredCorrectly(), leaderboardEntry.getAnsweredWrong(),
                    leaderboardEntry.getTotalQuestions(), leaderboardEntry.getDueDate(), leaderboardEntry.getStreak()));
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
        AnswerCounts answerCounts = calculateAnswerCounts(answeredQuestions);
        int correctAnswers = answerCounts.correct;
        int wrongAnswers = answerCounts.wrong;
        long totalAvailableQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(courseId);

        Course course = courseRepository.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).orElseGet(() -> {
            QuizTrainingLeaderboard entry = new QuizTrainingLeaderboard();
            entry.setUser(user);
            entry.setCourse(course);
            entry.setLeaderboardName(user.getFirstName()); // This will be adapted to the chosen name of the user later
            entry.setLeague(BRONZE_LEAGUE);
            entry.setScore(0);
            entry.setAnsweredCorrectly(0);
            entry.setAnsweredWrong(0);
            entry.setTotalQuestions(totalAvailableQuestions);
            entry.setDueDate(ZonedDateTime.now());
            entry.setStreak(0);
            return entry;
        });

        leaderboardEntry.setScore(leaderboardEntry.getScore() + delta);
        leaderboardEntry.setAnsweredCorrectly(leaderboardEntry.getAnsweredCorrectly() + correctAnswers);
        leaderboardEntry.setAnsweredWrong(leaderboardEntry.getAnsweredWrong() + wrongAnswers);
        leaderboardEntry.setTotalQuestions(totalAvailableQuestions);

        ZonedDateTime dueDate = findLatestDueDate(answeredQuestions);
        leaderboardEntry.setDueDate(dueDate);

        quizTrainingLeaderboardRepository.save(leaderboardEntry);
    }

    /**
     * Finds the earliest due date from a set of quiz question progress data.
     * If no due dates are available, returns the current time.
     *
     * @param progressDataSet the set of quiz question progress data to analyze
     * @return the earliest due date found or the current time if none exists
     */
    private ZonedDateTime findLatestDueDate(Set<QuizQuestionProgressData> progressDataSet) {
        return progressDataSet.stream().map(QuizQuestionProgressData::getDueDate).filter(date -> date != null).min(ZonedDateTime::compareTo).orElse(ZonedDateTime.now());
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
     * Calculates the number of correctly and incorrectly answered questions from a set of quiz question progress data.
     * A question is considered correctly answered if its last score is greater than or equal to 1.0,
     * otherwise it is counted as incorrectly answered.
     *
     * @param answeredQuestions the set of quiz question progress data to analyze
     * @return an AnswerCounts record containing the count of correct and wrong answers
     */
    private AnswerCounts calculateAnswerCounts(Set<QuizQuestionProgressData> answeredQuestions) {
        int correctCount = 0;
        int wrongCount = 0;

        for (QuizQuestionProgressData data : answeredQuestions) {
            if (data.getLastScore() >= 1.0) {
                correctCount++;
            }
            else {
                wrongCount++;
            }
        }

        return new AnswerCounts(correctCount, wrongCount);
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
        AnswerCounts answerCounts = calculateAnswerCounts(progressDataSet);
        int answeredCorrectly = answerCounts.correct;
        int answeredWrong = answerCounts.wrong;

        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).orElseGet(() -> {
            QuizTrainingLeaderboard entry = new QuizTrainingLeaderboard();
            entry.setUser(user);
            entry.setCourse(course);
            entry.setLeaderboardName(""); // This will be adapted to the chosen name of the user later
            entry.setLeague(BRONZE_LEAGUE);
            entry.setScore(score);
            entry.setAnsweredCorrectly(answeredCorrectly);
            entry.setAnsweredWrong(answeredWrong);
            entry.setTotalQuestions(totalAvailableQuestions);
            entry.setDueDate(ZonedDateTime.now());
            entry.setStreak(0);
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
     * Scheduled task that rebuilds all leaderboards for all courses weekly on Monday at 2 am.
     */
    // @Scheduled(cron = "0 0 2 ? * MON") - The exact scheduling will be determined later
    public void weeklyLeaderboardRebuild() {
        List<Course> allCourses = courseRepository.findAll();
        for (Course course : allCourses) {
            long totalQuestions = quizQuestionRepository.countOfQuizQuestionsAvailableForPractice(course.getId());
            initializeLeaderboardsForCourse(course.getId(), totalQuestions);
        }
    }
}
