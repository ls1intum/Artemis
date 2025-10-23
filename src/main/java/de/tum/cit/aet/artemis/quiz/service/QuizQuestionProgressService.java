package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionTrainingDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizQuestionProgressService {

    private final QuizQuestionProgressRepository quizQuestionProgressRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final QuizTrainingLeaderboardService quizTrainingLeaderboardService;

    public QuizQuestionProgressService(QuizQuestionProgressRepository quizQuestionProgressRepository, QuizQuestionRepository quizQuestionRepository,
            QuizTrainingLeaderboardService quizTrainingLeaderboardService) {
        this.quizQuestionProgressRepository = quizQuestionProgressRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizTrainingLeaderboardService = quizTrainingLeaderboardService;
    }

    /**
     * Adds a new attempt with the current score and answer time to the progress data.
     *
     * @param data       The progress data object for the question
     * @param score      The achieved score for the question
     * @param answeredAt The time when the question was answered
     */
    public void updateProgressWithNewAttempt(QuizQuestionProgressData data, double score, ZonedDateTime answeredAt) {
        QuizQuestionProgressData.Attempt attempt = new QuizQuestionProgressData.Attempt();
        attempt.setScore(score);
        attempt.setAnsweredAt(answeredAt);
        data.addAttempt(attempt);
        data.setLastScore(score);
    }

    /**
     * Updates the repetition count, easiness factor, interval, session count, priority, and box in the progress data.
     *
     * @param data             The progress data object for the question
     * @param score            The achieved score for the question
     * @param existingProgress The previous progress (can be null)
     * @param answeredAt       The time when the question was answered
     */
    public void updateProgressCalculations(QuizQuestionProgressData data, double score, QuizQuestionProgress existingProgress, ZonedDateTime answeredAt) {
        int repetition = calculateRepetition(score, data);
        data.setRepetition(repetition);

        double prevEasinessFactor = 2.5;
        int prevInterval = 1;
        int prevSessionCount = 0;

        if (existingProgress != null && existingProgress.getProgressJson() != null) {
            QuizQuestionProgressData prevData = existingProgress.getProgressJson();
            prevEasinessFactor = prevData.getEasinessFactor();
            prevInterval = prevData.getInterval();
            prevSessionCount = prevData.getSessionCount();
        }

        double easinessFactor = calculateEasinessFactor(score, prevEasinessFactor);
        data.setEasinessFactor(easinessFactor);
        int sessionCount = prevSessionCount + 1;
        data.setSessionCount(sessionCount);
        int interval = calculateInterval(easinessFactor, prevInterval, repetition);
        data.setInterval(interval);
        data.setDueDate(answeredAt.plusDays(interval));
        data.setBox(calculateBox(interval));
        data.setPriority(calculatePriority(sessionCount, interval, score));
    }

    /**
     * Get the sorted List of quiz questions based on their due date
     *
     * @param courseId     ID of the course for which the quiz questions are to be fetched
     * @param userId       ID of the user for whom the quiz questions are to be fetched
     * @param pageable     Pageable object with pagination information
     * @param questionIds  Optional set of question IDs to filter the questions
     * @param isNewSession Boolean indicating if it is a new training session or a continuation of an existing one
     * @return A list of quiz questions sorted by due date
     */
    public Slice<QuizQuestionTrainingDTO> getQuestionsForSession(long courseId, long userId, Pageable pageable, Set<Long> questionIds, boolean isNewSession) {
        ZonedDateTime now = ZonedDateTime.now();
        if (isNewSession) {
            Set<QuizQuestionProgress> allProgress = quizQuestionProgressRepository.findAllByUserIdAndCourseId(userId, courseId);

            questionIds = allProgress.stream().filter(progress -> {
                QuizQuestionProgressData data = progress.getProgressJson();
                return data != null && data.getDueDate() != null && data.getDueDate().isAfter(now);
            }).map(QuizQuestionProgress::getQuizQuestionId).collect(Collectors.toSet());
            isNewSession = false;
        }

        if (areQuestionsDue(courseId, questionIds.size())) {
            return loadDueQuestions(questionIds, courseId, pageable, isNewSession);
        }
        else {
            return loadAllPracticeQuestions(courseId, pageable, isNewSession);
        }
    }

    private boolean areQuestionsDue(Long courseId, int notDueCount) {
        long totalQuestionsCount = quizQuestionRepository.countAllPracticeQuizQuestionsByCourseId(courseId);

        return notDueCount < totalQuestionsCount;
    }

    private Slice<QuizQuestionTrainingDTO> loadDueQuestions(Set<Long> questionIds, Long courseId, Pageable pageable, boolean isNewSession) {
        Slice<QuizQuestion> questionPage = quizQuestionRepository.findAllDueQuestions(questionIds, courseId, pageable);

        return questionPage.map(question -> {
            QuizQuestionWithSolutionDTO dto = QuizQuestionWithSolutionDTO.of(question);
            return new QuizQuestionTrainingDTO(dto, true, questionIds, isNewSession);
        });
    }

    private Slice<QuizQuestionTrainingDTO> loadAllPracticeQuestions(Long courseId, Pageable pageable, boolean isNewSession) {
        Slice<QuizQuestion> questionPage = quizQuestionRepository.findAllPracticeQuizQuestionsByCourseId(courseId, pageable);

        return questionPage.map(question -> {
            QuizQuestionWithSolutionDTO dto = QuizQuestionWithSolutionDTO.of(question);
            return new QuizQuestionTrainingDTO(dto, false, null, isNewSession);
        });
    }

    /**
     * Calculate the repetition count, which represents how many times the question has been answered correctly in a row
     *
     * @param score The score achieved for the question
     * @param data  The progress data for the question
     * @return The repetition count for the question, which is the number of consecutive correct answers
     */
    public int calculateRepetition(double score, QuizQuestionProgressData data) {
        if (score != 1.0) {
            return 0;
        }
        int repetition = 0;
        List<QuizQuestionProgressData.Attempt> attempts = data.getAttempts();
        if (attempts != null) {
            for (int i = attempts.size() - 1; i >= 0; i--) {
                if (attempts.get(i).getScore() == 1.0) {
                    repetition++;
                }
                else {
                    break;
                }
            }
        }
        return repetition;
    }

    /**
     * Calculate the easiness factor based on the SM-2 algorithm
     *
     * @param score                  The score achieved for the question
     * @param previousEasinessFactor The easiness factor from the previous progress
     * @return The new easiness factor for the question, which shows how easy the question is for the user
     */
    public double calculateEasinessFactor(double score, double previousEasinessFactor) {
        double newEasinessFactor = previousEasinessFactor + (0.1 - (5 - score * 5) * (0.08 + (5 - score * 5) * 0.02));
        return Math.max(1.3, newEasinessFactor);
    }

    /**
     * Calculate the interval for the next session in which the question should be repeated
     *
     * @param easinessFactor   The easiness factor for the question
     * @param previousInterval The interval form the previous progress
     * @param repetition       The repetition count for the question
     * @return The interval to determine the next session in which the question should be repeated
     */
    public int calculateInterval(double easinessFactor, int previousInterval, int repetition) {
        if (repetition < 1) {
            return 0;
        }
        if (repetition == 1) {
            return 1;
        }
        if (repetition == 2) {
            return 2;
        }
        else {
            // Maximum interval for quiz question repetition in sessions
            int maxInterval = 30;
            return Math.min(Math.toIntExact(Math.round(previousInterval * easinessFactor)), maxInterval);
        }
    }

    /**
     * Calculate the priority for the question. The priority is used to determine the order in which questions should be presented.
     * A smaller number means higher priority
     *
     * @param sessionCount The number of sessions in which the question has been answered
     * @param interval     The interval for the next session in which the question should be repeated
     * @param score        The score achieved for the question
     * @return The priority for the question, which is higher the lower the number
     */
    public int calculatePriority(int sessionCount, int interval, double score) {
        if (sessionCount == 1 && score != 1.0) {
            return 1;
        }
        return sessionCount + interval;
    }

    /**
     * Calculate the box number based on the interval, which represents the learning progress of the student.
     * The boxes are based on the Leitner system and are used to keep track of the learning progress of the student.
     * They will be used for gamification elements
     *
     * @param interval The interval for the next session in which the question should be repeated
     * @return The box number for the question, which is used to determine the learning progress of the student
     */
    public int calculateBox(int interval) {
        if (interval <= 1) {
            return 1;
        }
        else if (interval == 2) {
            return 2;
        }
        else if (interval <= 4) {
            return 3;
        }
        else if (interval <= 7) {
            return 4;
        }
        else if (interval <= 15) {
            return 5;
        }
        else {
            return 6;
        }
    }

    /**
     *
     * @param courseId The id of the course for which the questions are to be checked
     * @return true if there are questions availble for training, false otherwise
     */
    public boolean questionsAvailableForTraining(Long courseId) {
        return quizQuestionRepository.areQuizQuestionsAvailableForPractice(courseId);
    }

    /**
     * saves the progress of a quiz question in the training mode
     *
     * @param question   The quiz question for which the progress is to be saved
     * @param userId     The id of the user
     * @param courseId   The id of the course
     * @param answer     The submitted answer for the question
     * @param answeredAt The time when the question was answered
     */

    public void saveProgressFromTraining(QuizQuestion question, long userId, long courseId, SubmittedAnswer answer, ZonedDateTime answeredAt) {
        QuizQuestionProgress existingProgress = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, question.getId()).orElse(new QuizQuestionProgress());
        QuizQuestionProgressData data = existingProgress.getProgressJson() != null ? existingProgress.getProgressJson() : new QuizQuestionProgressData();

        ZonedDateTime dueDate = data.getDueDate();
        if (dueDate == null || !dueDate.isAfter(answeredAt)) {
            existingProgress.setQuizQuestionId(question.getId());
            existingProgress.setUserId(userId);
            existingProgress.setCourseId(courseId);
            double score = question.getPoints() > 0 ? answer.getScoreInPoints() / question.getPoints() : 0.0;
            updateProgressWithNewAttempt(data, score, answeredAt);
            updateProgressCalculations(data, score, existingProgress, answeredAt);
            existingProgress.setProgressJson(data);
            existingProgress.setLastAnsweredAt(answeredAt);
            try {
                quizQuestionProgressRepository.save(existingProgress);
            }
            catch (DataIntegrityViolationException e) {
                updateExistingProgress(userId, question, data, answeredAt);
            }
            quizTrainingLeaderboardService.updateLeaderboardScore(userId, courseId, data);
        }
    }

    /**
     * Updates the existing progress entry for a given user and quiz question.
     * If a DataIntegrityViolationException occurs during the update, an IllegalStateException is thrown.
     *
     * @param userId     The ID of the user whose progress is being updated
     * @param question   The quiz question for which the progress is being updated
     * @param data       The updated progress data for the quiz question
     * @param answeredAt The time when the question was answered
     * @throws IllegalStateException if the progress entry does not exist or a data integrity violation occurs
     */
    public void updateExistingProgress(long userId, QuizQuestion question, QuizQuestionProgressData data, ZonedDateTime answeredAt) {
        try {
            QuizQuestionProgress progress = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, question.getId())
                    .orElseThrow(() -> new IllegalStateException("Progress entry should exist but was not found."));
            progress.setLastAnsweredAt(answeredAt);
            progress.setProgressJson(data);
            quizQuestionProgressRepository.save(progress);
        }
        catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Error when trying to update existing progress", e);
        }
    }

}
