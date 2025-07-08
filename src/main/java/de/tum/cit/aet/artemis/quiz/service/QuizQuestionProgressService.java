package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizQuestionProgressService {

    private static final Logger log = LoggerFactory.getLogger(QuizQuestionProgressService.class);

    private final QuizQuestionProgressRepository quizQuestionProgressRepository;

    private final UserRepository userRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    public QuizQuestionProgressService(QuizQuestionProgressRepository quizQuestionProgressRepository, UserRepository userRepository,
            QuizQuestionRepository quizQuestionRepository) {
        this.quizQuestionProgressRepository = quizQuestionProgressRepository;
        this.userRepository = userRepository;
        this.quizQuestionRepository = quizQuestionRepository;
    }

    /**
     * Fetch the necessary data for the quiz question progress from the quiz exercise and submission
     * Set the progress data for each answered question
     *
     * @param quizExercise   The quiz exercise for which the progress is to be retrieved
     * @param quizSubmission The quiz submission containing the user's answers
     * @param participation  The student participation for the submission
     */
    public void retrieveProgressFromResultAndSubmission(QuizExercise quizExercise, QuizSubmission quizSubmission, StudentParticipation participation) {
        ZonedDateTime lastAnsweredAt = quizSubmission.getSubmissionDate();
        Map<QuizQuestion, QuizQuestionProgressData> answeredQuestions = new HashMap<>();
        Long userId = participation.getParticipant().getId();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            SubmittedAnswer answer = quizSubmission.getSubmittedAnswerForQuestion(question);
            if (answer == null) {
                continue;
            }
            QuizQuestionProgress existingProgress = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, question.getId()).orElse(null);
            QuizQuestionProgressData data = new QuizQuestionProgressData();
            QuizQuestionProgressData.Attempt attempt = new QuizQuestionProgressData.Attempt();
            if (existingProgress != null) {
                data = existingProgress.getProgressJson();
            }

            // Calculate the achieved score for the question
            double score = question.scoreForAnswer(answer) / question.getPoints();
            data.setLastScore(score);

            // Set the attempt
            attempt.setScore(score);
            attempt.setAnsweredAt(quizSubmission.getSubmissionDate());

            data.addAttempt(attempt);

            // Set the repetition
            int repetition = calculateRepetition(score, data);
            data.setRepetition(repetition);

            // Set the easiness factor based on the previous progress or default value & set the interval days & set session count
            double prevEasinessFactor = 2.5;
            int prevInterval = 1;
            int prevSessionCount = 0;

            if (existingProgress != null) {
                prevEasinessFactor = existingProgress.getProgressJson().getEasinessFactor();
                prevInterval = existingProgress.getProgressJson().getInterval();
                prevSessionCount = existingProgress.getProgressJson().getSessionCount();
            }
            double easinessFactor = calculateEasinessFactor(score, prevEasinessFactor);
            data.setEasinessFactor(easinessFactor);
            int interval = calculateInterval(easinessFactor, prevInterval, repetition);
            data.setInterval(interval);
            int sessionCount = prevSessionCount + 1;
            data.setSessionCount(sessionCount);

            // Set new due date based on the last answered time and calculated interval
            data.setPriority(calculatePriority(sessionCount, interval, score));

            // Set the box for the question
            data.setBox(calculateBox(interval));

            // Add the question and its progress data to the map
            answeredQuestions.put(question, data);
        }
        updateProgress(answeredQuestions, lastAnsweredAt, userId);
    }

    /**
     * The function updates the progress of quiz questions and save it to the database
     *
     * @param answeredQuestions List of quiz questions that were answered
     * @param lastAnsweredAt    Time when the question was last answered
     * @param userId            The ID of the user for the participation
     */
    public void updateProgress(Map<QuizQuestion, QuizQuestionProgressData> answeredQuestions, ZonedDateTime lastAnsweredAt, Long userId) {
        answeredQuestions.forEach((question, data) -> {
            QuizQuestionProgress progress = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, question.getId()).orElse(new QuizQuestionProgress());
            progress.setUserId(userId);
            progress.setQuizQuestionId(question.getId());
            progress.setProgressJson(data);
            progress.setLastAnsweredAt(lastAnsweredAt);
            save(progress);
        });
    }

    /**
     * Save the progress of a quiz question to the database
     *
     * @param quizQuestionProgress The progress object containing the user's progress for a quiz question
     * @return The saved QuizQuestionProgress object
     */
    public QuizQuestionProgress save(QuizQuestionProgress quizQuestionProgress) {
        return quizQuestionProgressRepository.save(quizQuestionProgress);
    }

    /**
     * Get the sorted List of 10 quiz questions based on their priority
     *
     * @param courseId ID of the course for which the quiz questions are to be fetched
     * @return A list of 10 quiz questions sorted by priority
     */
    public List<QuizQuestion> getQuestionsForSession(Long courseId) {
        Set<QuizQuestion> allQuestions = getQuizQuestions(courseId);
        long userId = getUserId();
        List<QuizQuestion> selectedQuestions = allQuestions.stream().sorted(Comparator.comparingInt(q -> {
            return quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, q.getId()).map(progress -> progress.getProgressJson().getPriority()).orElse(0);
        })).limit(10).toList();

        return selectedQuestions;
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
            for (QuizQuestionProgressData.Attempt attempt : attempts) {
                if (attempt.getScore() == 1.0) {
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
        double newEasinessFactor = previousEasinessFactor + (0.1 - (5 - score * 5) * (0.08 + (1 - score * 5) * 0.02));
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
        if (repetition <= 1) {
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
        if (interval == 1) {
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

    // Getter
    public Set<QuizQuestion> getQuizQuestions(Long courseId) {
        return quizQuestionRepository.findAllQuizQuestionsByCourseId(courseId);
    }

    public long getUserId() {
        return userRepository.getUser().getId();
    }

}
