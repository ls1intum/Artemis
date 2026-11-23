package de.tum.cit.aet.artemis.atlas.service.practice;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeAnswerOptionDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeQuestionDTO;
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
import de.tum.cit.aet.artemis.hyperion.api.QuizQuestionPoolApi;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCandidateDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.EntityIdRefDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.MultipleChoiceSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

/**
 * Service managing on-demand adaptive practice quiz delivery and logging from Hyperion's AI Quiz Question Pool.
 * Assembles quizzes enforcing an equal 50/50 split of adaptive and non-adaptive items,
 * and records all practice attempts in strict isolation from Atlas competency mastery and core grades.
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

    private final Optional<QuizQuestionPoolApi> quizQuestionPoolApi;

    private final PracticeAllocationPolicy practiceAllocationPolicy;

    private final JsonMapper jsonMapper;

    public PracticeQuizService(PracticeSessionRepository practiceSessionRepository, PracticeLogRepository practiceLogRepository,
            CourseCompetencyRepository courseCompetencyRepository, Optional<QuizQuestionPoolApi> quizQuestionPoolApi, PracticeAllocationPolicy practiceAllocationPolicy,
            JsonMapper jsonMapper) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.practiceLogRepository = practiceLogRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.quizQuestionPoolApi = quizQuestionPoolApi;
        this.practiceAllocationPolicy = practiceAllocationPolicy;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Starts an on-demand practice quiz session with questions allocated from the AI question pool.
     *
     * @param user   the student requesting practice
     * @param course the course for which practice is requested
     * @return the created practice session DTO with solution-hidden questions
     */
    public PracticeSessionDTO startPracticeSession(User user, Course course) {
        if (quizQuestionPoolApi.isEmpty()) {
            throw new BadRequestAlertException("Quiz question pool service is not available", "practiceQuiz", "poolNotAvailable");
        }

        List<QuizQuestionPoolCandidateDTO> availableCandidates = quizQuestionPoolApi.get().getPracticeEligibleQuestionCandidates(course.getId());
        if (availableCandidates.isEmpty()) {
            throw new BadRequestAlertException("No quiz questions available in the question pool for this course", "practiceQuiz", "noQuestionsAvailable");
        }

        int totalCount = Math.min(DEFAULT_PRACTICE_QUIZ_SIZE, availableCandidates.size());
        Set<CourseCompetency> competencies = courseCompetencyRepository.findAllForCourse(course.getId());

        List<QuizQuestionPoolCandidateDTO> allocatedCandidates = practiceAllocationPolicy.allocateQuestions(user, competencies, totalCount, availableCandidates);

        PracticeSession session = new PracticeSession();
        session.setUser(user);
        session.setCourse(course);
        session.setStartTime(ZonedDateTime.now());
        PracticeSession savedSession = practiceSessionRepository.save(session);

        List<PracticeLog> logs = new ArrayList<>();
        for (QuizQuestionPoolCandidateDTO candidate : allocatedCandidates) {
            PracticeLog logEntry = new PracticeLog();
            logEntry.setPracticeSession(savedSession);
            logEntry.setPoolItemId(candidate.id());
            logEntry.setCompetencyId(candidate.competencyId());
            logEntry.setDifficulty(candidate.difficulty() != null ? candidate.difficulty().name() : null);
            logEntry.setAdaptive(false);
            logs.add(logEntry);
        }

        practiceLogRepository.saveAll(logs);

        List<Long> allocatedIds = allocatedCandidates.stream().map(QuizQuestionPoolCandidateDTO::id).toList();
        List<QuizQuestionPoolItem> allocatedItems = quizQuestionPoolApi.get().findAllById(allocatedIds);
        Map<Long, QuizQuestionPoolItem> itemsById = allocatedItems.stream().collect(Collectors.toMap(QuizQuestionPoolItem::getId, Function.identity(), (a, b) -> a));

        List<PracticeQuestionDTO> questionDTOs = logs.stream().map(l -> toPracticeQuestionDTO(itemsById.get(l.getPoolItemId()), false)).filter(Objects::nonNull).toList();

        return PracticeSessionDTO.of(savedSession, questionDTOs);
    }

    /**
     * Submits student responses for a practice quiz session, computes scores, and records the responses.
     * Guaranteed never to modify Atlas competency mastery or core grade records.
     *
     * @param courseId      the ID of the course
     * @param sessionId     the ID of the practice session
     * @param user          the student submitting responses
     * @param submissionDTO the submitted answers
     * @return the review DTO with solutions, explanations, and scores
     */
    public PracticeSessionReviewDTO submitPracticeSession(long courseId, long sessionId, User user, PracticeSubmissionDTO submissionDTO) {
        PracticeSession session = practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(sessionId, courseId, user.getId());
        if (session.getCompletedAt() != null) {
            throw new BadRequestAlertException("This practice session has already been completed", "practiceQuiz", "sessionAlreadyCompleted");
        }

        List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(sessionId);
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
            maxPoints += 1.0;
            Long poolItemId = logEntry.getPoolItemId();
            QuizQuestionPoolItem item = quizQuestionPoolApi.flatMap(api -> api.findById(poolItemId)).orElse(null);

            SubmittedAnswerFromLiveClientDTO answerDto = answersByQuestionId.get(poolItemId);
            double questionScore = 0.0;
            String answerJson = null;

            if (item != null && answerDto != null) {
                questionScore = evaluatePoolQuestionAnswer(item, answerDto);
                try {
                    answerJson = jsonMapper.writeValueAsString(answerDto);
                }
                catch (Exception e) {
                    log.error("Failed to serialize submitted answer for question {}", poolItemId, e);
                }
            }

            totalScore += questionScore;
            logEntry.setScore(questionScore);
            logEntry.setSubmittedAnswer(answerJson);
            logEntry.setAnsweredAt(now);

            PracticeQuestionDTO questionDTO = item != null ? toPracticeQuestionDTO(item, true) : null;
            reviewQuestions.add(new PracticeQuestionReviewDTO(questionDTO, logEntry.isAdaptive(), questionScore, answerJson, now));
        }

        session.setScore(totalScore);
        session.setMaxPoints(maxPoints);
        session.setCompletedAt(now);

        practiceLogRepository.saveAll(logs);
        PracticeSession completedSession = practiceSessionRepository.save(session);

        return PracticeSessionReviewDTO.of(completedSession, reviewQuestions);
    }

    /**
     * Submits student responses for a practice quiz session, looking up the session by sessionId and user.
     *
     * @param sessionId     the ID of the practice session
     * @param user          the student submitting responses
     * @param submissionDTO the submitted answers
     * @return the review DTO with solutions, explanations, and scores
     */
    public PracticeSessionReviewDTO submitPracticeSession(long sessionId, User user, PracticeSubmissionDTO submissionDTO) {
        PracticeSession session = practiceSessionRepository.findByIdAndUserIdOrElseThrow(sessionId, user.getId());
        return submitPracticeSession(session.getCourse().getId(), sessionId, user, submissionDTO);
    }

    /**
     * Evaluates whether the student's selected options match the correct answers for a pool item.
     *
     * @param item      the pool item
     * @param answerDto the submitted answer from the client
     * @return 1.0 if correct, 0.0 if not
     */
    public double evaluatePoolQuestionAnswer(QuizQuestionPoolItem item, SubmittedAnswerFromLiveClientDTO answerDto) {
        if (item.getQuestion() == null || !(answerDto instanceof MultipleChoiceSubmittedAnswerFromLiveClientDTO mcAnswer)) {
            return 0.0;
        }

        Set<Long> selectedIds = mcAnswer.selectedOptions() != null ? mcAnswer.selectedOptions().stream().map(EntityIdRefDTO::id).collect(Collectors.toSet()) : Set.of();

        List<GeneratedQuizAnswerOptionDTO> options = item.getQuestion().options();
        Set<Long> correctOptionIndices = new HashSet<>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).correct()) {
                correctOptionIndices.add((long) i);
            }
        }

        return selectedIds.equals(correctOptionIndices) ? 1.0 : 0.0;
    }

    /**
     * Retrieves an already completed practice session for student review, including questions, solutions, and explanations.
     * Verifies that the session belongs to the specified course and has actually been completed to prevent leaking solutions.
     *
     * @param courseId  the ID of the course
     * @param sessionId the ID of the practice session
     * @param user      the student reviewing the session
     * @return the review DTO
     */
    public PracticeSessionReviewDTO getPracticeSessionForReview(long courseId, long sessionId, User user) {
        PracticeSession session = practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(sessionId, courseId, user.getId());
        if (session.getCompletedAt() == null) {
            throw new BadRequestAlertException("This practice session is not completed yet", "practiceQuiz", "sessionNotCompleted");
        }
        List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(sessionId);

        List<PracticeQuestionReviewDTO> reviewQuestions = logs.stream().map(logEntry -> {
            QuizQuestionPoolItem item = quizQuestionPoolApi.flatMap(api -> api.findById(logEntry.getPoolItemId())).orElse(null);
            PracticeQuestionDTO questionDTO = item != null ? toPracticeQuestionDTO(item, true) : null;
            return new PracticeQuestionReviewDTO(questionDTO, logEntry.isAdaptive(), logEntry.getScore(), logEntry.getSubmittedAnswer(), logEntry.getAnsweredAt());
        }).toList();

        return PracticeSessionReviewDTO.of(session, reviewQuestions);
    }

    /**
     * Retrieves an already completed practice session for student review, looking up the session by sessionId and user.
     *
     * @param sessionId the ID of the practice session
     * @param user      the student reviewing the session
     * @return the review DTO
     */
    public PracticeSessionReviewDTO getPracticeSessionForReview(long sessionId, User user) {
        PracticeSession session = practiceSessionRepository.findByIdAndUserIdOrElseThrow(sessionId, user.getId());
        return getPracticeSessionForReview(session.getCourse().getId(), sessionId, user);
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
        List<PracticeLog> logs = practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(session.getId());
        List<PracticeQuestionDTO> questions = logs.stream().map(l -> {
            QuizQuestionPoolItem item = quizQuestionPoolApi.flatMap(api -> api.findById(l.getPoolItemId())).orElse(null);
            return item != null ? toPracticeQuestionDTO(item, false) : null;
        }).filter(Objects::nonNull).toList();

        return Optional.of(PracticeSessionDTO.of(session, questions));
    }

    /**
     * Converts a QuizQuestionPoolItem to a PracticeQuestionDTO, with or without solutions.
     *
     * @param item            the pool item
     * @param includeSolution whether to include isCorrect flags and explanations
     * @return the PracticeQuestionDTO
     */
    public PracticeQuestionDTO toPracticeQuestionDTO(QuizQuestionPoolItem item, boolean includeSolution) {
        if (item == null || item.getQuestion() == null) {
            return null;
        }

        GeneratedQuizQuestionDTO question = item.getQuestion();
        List<PracticeAnswerOptionDTO> optionDTOs = new ArrayList<>();
        List<GeneratedQuizAnswerOptionDTO> options = question.options();
        for (int i = 0; i < options.size(); i++) {
            GeneratedQuizAnswerOptionDTO opt = options.get(i);
            if (includeSolution) {
                optionDTOs.add(PracticeAnswerOptionDTO.withSolution(i, opt.text(), opt.hint(), opt.correct(), opt.explanation()));
            }
            else {
                optionDTOs.add(PracticeAnswerOptionDTO.withoutSolution(i, opt.text(), opt.hint()));
            }
        }

        boolean singleChoice = question.type() == QuizQuestionGenerationType.SINGLE_CHOICE;
        String difficulty = item.getDifficulty() != null ? item.getDifficulty().name() : "MEDIUM";
        String explanation = includeSolution ? question.explanation() : null;

        return new PracticeQuestionDTO(item.getId(), question.title(), question.questionText(), question.hint(), explanation, 1.0, "multiple-choice", optionDTOs, singleChoice,
                item.getCompetencyId(), difficulty);
    }
}
