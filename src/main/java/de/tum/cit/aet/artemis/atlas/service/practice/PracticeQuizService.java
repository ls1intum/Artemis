package de.tum.cit.aet.artemis.atlas.service.practice;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeLog;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeSession;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeQuestionReviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionReviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSubmissionDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PracticeLogRepository;
import de.tum.cit.aet.artemis.atlas.repository.PracticeSessionRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.quiz.api.QuizQuestionApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

/**
 * Service managing on-demand adaptive practice quiz delivery and logging.
 * Assembles quizzes enforcing an equal 50/50 split of adaptive and non-adaptive items,
 * and records all practice attempts in isolation from Atlas mastery estimates and core grades.
 */
@Service
@Conditional(AtlasEnabled.class)
@Lazy
public class PracticeQuizService {

    private static final Logger log = LoggerFactory.getLogger(PracticeQuizService.class);

    public static final int DEFAULT_PRACTICE_QUIZ_SIZE = 10;

    private final PracticeSessionRepository practiceSessionRepository;

    private final PracticeLogRepository practiceLogRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final QuizQuestionApi quizQuestionApi;

    private final PracticeAllocationPolicy practiceAllocationPolicy;

    private final JsonMapper jsonMapper;

    public PracticeQuizService(PracticeSessionRepository practiceSessionRepository, PracticeLogRepository practiceLogRepository,
            CourseCompetencyRepository courseCompetencyRepository, QuizQuestionApi quizQuestionApi, PracticeAllocationPolicy practiceAllocationPolicy, JsonMapper jsonMapper) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.practiceLogRepository = practiceLogRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.quizQuestionApi = quizQuestionApi;
        this.practiceAllocationPolicy = practiceAllocationPolicy;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Starts an on-demand practice quiz session with a 50/50 split between adaptive and control questions.
     *
     * @param user   the student requesting practice
     * @param course the course for which practice is requested
     * @return the created practice session DTO with solution-hidden questions
     */
    public PracticeSessionDTO startPracticeSession(User user, Course course) {
        List<QuizQuestion> availableQuestions = quizQuestionApi.findAllPracticeQuestionsForCourse(course.getId());
        if (availableQuestions.isEmpty()) {
            throw new BadRequestAlertException("No quiz questions available for practice in this course", "practiceQuiz", "noQuestionsAvailable");
        }

        int totalCount = Math.min(DEFAULT_PRACTICE_QUIZ_SIZE, availableQuestions.size());
        int adaptiveCount = totalCount / 2;
        int controlCount = totalCount - adaptiveCount;

        Set<CourseCompetency> competencies = courseCompetencyRepository.findAllForCourse(course.getId());

        // Allocate adaptive items via the pluggable policy
        List<QuizQuestion> allocatedAdaptive = practiceAllocationPolicy.allocateQuestions(user, competencies, adaptiveCount, availableQuestions);

        // Select control items uniformly from the remaining pool
        Set<Long> adaptiveIds = allocatedAdaptive.stream().map(QuizQuestion::getId).collect(Collectors.toSet());
        List<QuizQuestion> remainingForControl = availableQuestions.stream().filter(q -> !adaptiveIds.contains(q.getId())).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(remainingForControl);
        List<QuizQuestion> allocatedControl = remainingForControl.stream().limit(controlCount).toList();

        PracticeSession session = new PracticeSession();
        session.setUser(user);
        session.setCourse(course);
        session.setStartTime(ZonedDateTime.now());
        PracticeSession savedSession = practiceSessionRepository.save(session);

        List<PracticeLog> logs = new ArrayList<>();
        for (QuizQuestion question : allocatedAdaptive) {
            PracticeLog logEntry = new PracticeLog();
            logEntry.setPracticeSession(savedSession);
            logEntry.setQuizQuestion(question);
            logEntry.setAdaptive(true);
            logs.add(logEntry);
        }
        for (QuizQuestion question : allocatedControl) {
            PracticeLog logEntry = new PracticeLog();
            logEntry.setPracticeSession(savedSession);
            logEntry.setQuizQuestion(question);
            logEntry.setAdaptive(false);
            logs.add(logEntry);
        }

        // Interleave / shuffle logs so the student experience is seamless and does not reveal question conditions
        Collections.shuffle(logs);
        practiceLogRepository.saveAll(logs);

        List<QuizQuestionWithoutSolutionDTO> questionDTOs = logs.stream().map(l -> QuizQuestionWithoutSolutionDTO.of(l.getQuizQuestion())).toList();

        return PracticeSessionDTO.of(savedSession, questionDTOs);
    }

    /**
     * Submits student responses for a practice quiz session, computes scores, and records the responses.
     * Guaranteed never to modify Atlas competency progress or core grade records.
     *
     * @param sessionId     the ID of the practice session
     * @param user          the student submitting responses
     * @param submissionDTO the submitted answers
     * @return the review DTO with solutions, explanations, and scores
     */
    public PracticeSessionReviewDTO submitPracticeSession(long sessionId, User user, PracticeSubmissionDTO submissionDTO) {
        PracticeSession session = practiceSessionRepository.findByIdAndUserIdOrElseThrow(sessionId, user.getId());
        if (session.getCompletedAt() != null) {
            throw new BadRequestAlertException("This practice session has already been completed", "practiceQuiz", "sessionAlreadyCompleted");
        }

        List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdWithQuestion(sessionId);
        if (logs.isEmpty()) {
            throw new EntityNotFoundException("No questions found for practice session " + sessionId);
        }

        Map<Long, SubmittedAnswerFromLiveClientDTO> answersByQuestionId = submissionDTO != null && submissionDTO.answers() != null
                ? submissionDTO.answers().stream().filter(a -> a != null && a.quizQuestion() != null && a.quizQuestion().id() != null).collect(
                        Collectors.toMap(a -> a.quizQuestion().id(), Function.identity(), (a1, a2) -> a1))
                : Map.of();

        double totalScore = 0.0;
        double maxPoints = 0.0;
        ZonedDateTime now = ZonedDateTime.now();
        List<PracticeQuestionReviewDTO> reviewQuestions = new ArrayList<>();

        for (PracticeLog logEntry : logs) {
            QuizQuestion question = logEntry.getQuizQuestion();
            maxPoints += question.getPoints();

            SubmittedAnswerFromLiveClientDTO answerDto = answersByQuestionId.get(question.getId());
            double questionScore = 0.0;
            String answerJson = null;

            if (answerDto != null) {
                questionScore = quizQuestionApi.evaluateSubmittedAnswer(question, answerDto);
                try {
                    answerJson = jsonMapper.writeValueAsString(answerDto);
                }
                catch (Exception e) {
                    log.error("Failed to serialize submitted answer for question {}", question.getId(), e);
                }
            }

            totalScore += questionScore;
            logEntry.setScore(questionScore);
            logEntry.setSubmittedAnswer(answerJson);
            logEntry.setAnsweredAt(now);

            reviewQuestions.add(new PracticeQuestionReviewDTO(QuizQuestionWithSolutionDTO.of(question), logEntry.isAdaptive(), questionScore, answerJson, now));
        }

        session.setScore(totalScore);
        session.setMaxPoints(maxPoints);
        session.setCompletedAt(now);

        practiceLogRepository.saveAll(logs);
        PracticeSession completedSession = practiceSessionRepository.save(session);

        return PracticeSessionReviewDTO.of(completedSession, reviewQuestions);
    }

    /**
     * Retrieves an already completed practice session for student review, including questions, solutions, and explanations.
     *
     * @param sessionId the ID of the practice session
     * @param user      the student reviewing the session
     * @return the review DTO
     */
    public PracticeSessionReviewDTO getPracticeSessionForReview(long sessionId, User user) {
        PracticeSession session = practiceSessionRepository.findByIdAndUserIdOrElseThrow(sessionId, user.getId());
        List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdWithQuestion(sessionId);

        List<PracticeQuestionReviewDTO> reviewQuestions = logs.stream().map(logEntry -> new PracticeQuestionReviewDTO(QuizQuestionWithSolutionDTO.of(logEntry.getQuizQuestion()),
                logEntry.isAdaptive(), logEntry.getScore(), logEntry.getSubmittedAnswer(), logEntry.getAnsweredAt())).toList();

        return PracticeSessionReviewDTO.of(session, reviewQuestions);
    }

    /**
     * Lists summaries of all past practice sessions for the student in a course.
     *
     * @param courseId the ID of the course
     * @param user     the student requesting history
     * @return list of session summaries
     */
    public List<PracticeSessionSummaryDTO> getPracticeSessionsForStudent(long courseId, User user) {
        List<PracticeSession> sessions = practiceSessionRepository.findAllByUserIdAndCourseIdOrderByStartTimeDesc(user.getId(), courseId);
        return sessions.stream().map(session -> {
            List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(session.getId());
            return PracticeSessionSummaryDTO.of(session, logs.size());
        }).toList();
    }

    /**
     * Finds the latest in-progress practice session if one exists.
     *
     * @param courseId the ID of the course
     * @param user     the student
     * @return optional containing the active session DTO if present
     */
    public Optional<PracticeSessionDTO> getLatestUnfinishedSession(long courseId, User user) {
        List<PracticeSession> sessions = practiceSessionRepository.findAllByUserIdAndCourseIdOrderByStartTimeDesc(user.getId(), courseId);
        Optional<PracticeSession> unfinished = sessions.stream().filter(s -> s.getCompletedAt() == null).findFirst();
        if (unfinished.isEmpty()) {
            return Optional.empty();
        }
        PracticeSession session = unfinished.get();
        List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdWithQuestion(session.getId());
        List<QuizQuestionWithoutSolutionDTO> questions = logs.stream().map(l -> QuizQuestionWithoutSolutionDTO.of(l.getQuizQuestion())).toList();
        return Optional.of(PracticeSessionDTO.of(session, questions));
    }
}
