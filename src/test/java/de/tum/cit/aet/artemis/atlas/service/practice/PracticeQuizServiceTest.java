package de.tum.cit.aet.artemis.atlas.service.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeLog;
import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeSession;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionReviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSubmissionDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PracticeLogRepository;
import de.tum.cit.aet.artemis.atlas.repository.PracticeSessionRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.api.QuizQuestionPoolApi;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCandidateDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.EntityIdRefDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.MultipleChoiceSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

@ExtendWith(MockitoExtension.class)
class PracticeQuizServiceTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeLogRepository practiceLogRepository;

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private QuizQuestionPoolApi quizQuestionPoolApi;

    private PracticeAllocationPolicy practiceAllocationPolicy;

    private JsonMapper jsonMapper;

    private PracticeQuizService practiceQuizService;

    private User student;

    private Course course;

    private List<QuizQuestionPoolItem> poolItems;

    @BeforeEach
    void setUp() {
        practiceAllocationPolicy = new UniformBaselinePolicy();
        jsonMapper = new JsonMapper();
        practiceQuizService = new PracticeQuizService(practiceSessionRepository, practiceLogRepository, courseCompetencyRepository, Optional.of(quizQuestionPoolApi),
                practiceAllocationPolicy, jsonMapper);

        student = new User();
        student.setId(10L);

        course = new Course();
        course.setId(20L);

        poolItems = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            QuizQuestionPoolItem item = new QuizQuestionPoolItem();
            item.setId(i);
            item.setCompetencyId(100L);
            item.setDifficulty(QuizQuestionDifficulty.MEDIUM);

            GeneratedQuizAnswerOptionDTO opt1 = new GeneratedQuizAnswerOptionDTO("Option 1", true, "Hint 1", "Explanation 1");
            GeneratedQuizAnswerOptionDTO opt2 = new GeneratedQuizAnswerOptionDTO("Option 2", false, "Hint 2", "Explanation 2");
            GeneratedQuizQuestionDTO question = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, "Question " + i, "Text " + i, List.of(opt1, opt2), "Hint",
                    "Explanation");
            item.setQuestion(question);

            poolItems.add(item);
        }
    }

    @Test
    void testStartPracticeSessionAllocatesQuestionsFromPool() {
        List<QuizQuestionPoolCandidateDTO> candidates = poolItems.stream().map(item -> new QuizQuestionPoolCandidateDTO(item.getId(), item.getCompetencyId(), item.getDifficulty()))
                .toList();

        when(quizQuestionPoolApi.getPracticeEligibleQuestionCandidates(course.getId())).thenReturn(candidates);
        when(quizQuestionPoolApi.findAllById(anyCollection())).thenReturn(poolItems);
        when(courseCompetencyRepository.findAllForCourse(course.getId())).thenReturn(Set.of());
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession ps = invocation.getArgument(0);
            ps.setId(100L);
            return ps;
        });

        PracticeSessionDTO result = practiceQuizService.startPracticeSession(student, course);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.questions()).hasSize(10);
        // Ensure question explanation is hidden during active quiz
        assertThat(result.questions().getFirst().explanation()).isNull();

        // The logs are inserted together with the session through the cascade, not in a separate save
        ArgumentCaptor<PracticeSession> sessionCaptor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getUser()).isEqualTo(student);
        verify(practiceLogRepository, never()).saveAll(anyList());
    }

    @Test
    void testStartPracticeSessionThrowsWhenNoQuestionsAvailable() {
        when(quizQuestionPoolApi.getPracticeEligibleQuestionCandidates(course.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> practiceQuizService.startPracticeSession(student, course)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("No quiz questions available");
    }

    @Test
    void testSubmitPracticeSessionComputesScoresAndIsolatesFromAtlas() {
        PracticeSession session = new PracticeSession();
        session.setId(100L);
        session.setUser(student);
        session.setCourse(course);
        session.setStartTime(ZonedDateTime.now().minusMinutes(10));

        PracticeLog log1 = new PracticeLog();
        log1.setId(1L);
        log1.setPracticeSession(session);
        log1.setPoolItemId(poolItems.get(0).getId());
        log1.setAdaptive(true);

        PracticeLog log2 = new PracticeLog();
        log2.setId(2L);
        log2.setPracticeSession(session);
        log2.setPoolItemId(poolItems.get(1).getId());
        log2.setAdaptive(false);

        List<PracticeLog> logs = List.of(log1, log2);

        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);
        when(practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(100L)).thenReturn(logs);
        when(quizQuestionPoolApi.findAllById(anyCollection())).thenReturn(poolItems.subList(0, 2));
        when(practiceLogRepository.completeSessionWithLogs(eq(100L), eq(1.0), eq(2.0), any(ZonedDateTime.class), eq(logs))).thenReturn(true);

        // Answer 1: selected option 0 (correct) -> score 1.0
        SubmittedAnswerFromLiveClientDTO answer1 = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolItems.get(0).getId()), Set.of(new EntityIdRefDTO(0L)));
        // Answer 2: selected option 1 (incorrect) -> score 0.0
        SubmittedAnswerFromLiveClientDTO answer2 = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolItems.get(1).getId()), Set.of(new EntityIdRefDTO(1L)));
        PracticeSubmissionDTO submission = new PracticeSubmissionDTO(List.of(answer1, answer2));

        PracticeSessionReviewDTO review = practiceQuizService.submitPracticeSession(course.getId(), 100L, student, submission);

        assertThat(review).isNotNull();
        assertThat(review.score()).isEqualTo(1.0);
        assertThat(review.maxPoints()).isEqualTo(2.0);
        assertThat(review.completedAt()).isNotNull();
        assertThat(review.questions()).hasSize(2);
        // Ensure question explanation is exposed in review
        assertThat(review.questions().getFirst().question().explanation()).isEqualTo("Explanation");

        verify(practiceLogRepository).completeSessionWithLogs(eq(100L), eq(1.0), eq(2.0), any(ZonedDateTime.class), eq(logs));
        verify(practiceSessionRepository, never()).save(any(PracticeSession.class));
        assertThat(log1.getScore()).isEqualTo(1.0);
        assertThat(log2.getScore()).isEqualTo(0.0);
    }

    @Test
    void testSubmitRejectsWhenConcurrentSubmissionCompletedSessionFirst() {
        PracticeSession session = createUnfinishedSession();
        List<PracticeLog> logs = List.of(createLog(session, 1L, poolItems.getFirst().getId()));

        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);
        when(practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(100L)).thenReturn(logs);
        when(quizQuestionPoolApi.findAllById(anyCollection())).thenReturn(poolItems.subList(0, 1));
        when(practiceLogRepository.completeSessionWithLogs(eq(100L), eq(0.0), eq(1.0), any(ZonedDateTime.class), eq(logs))).thenReturn(false);

        assertThatThrownBy(() -> practiceQuizService.submitPracticeSession(course.getId(), 100L, student, new PracticeSubmissionDTO(List.of())))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("already been completed");
    }

    @Test
    void testSubmitAndReviewSkipQuestionsWhosePoolItemWasDeleted() {
        PracticeSession session = createUnfinishedSession();
        PracticeLog kept = createLog(session, 1L, poolItems.getFirst().getId());
        PracticeLog deleted = createLog(session, 2L, 999L);
        List<PracticeLog> logs = List.of(kept, deleted);

        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);
        when(practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(100L)).thenReturn(logs);
        when(quizQuestionPoolApi.findAllById(anyCollection())).thenReturn(poolItems.subList(0, 1));
        when(practiceLogRepository.completeSessionWithLogs(eq(100L), eq(0.0), eq(1.0), any(ZonedDateTime.class), eq(logs))).thenReturn(true);

        PracticeSessionReviewDTO review = practiceQuizService.submitPracticeSession(course.getId(), 100L, student, new PracticeSubmissionDTO(List.of()));

        assertThat(review.maxPoints()).isEqualTo(1.0);
        assertThat(review.questions()).hasSize(1);
        assertThat(review.questions().getFirst().question()).isNotNull();

        PracticeSessionReviewDTO laterReview = practiceQuizService.getPracticeSessionForReview(course.getId(), 100L, student);
        assertThat(laterReview.questions()).hasSize(1);
        assertThat(laterReview.questions().getFirst().question().id()).isEqualTo(poolItems.getFirst().getId());
    }

    @Test
    void testGetUnfinishedSessionReturnsSelectedSessionWithoutSolutions() {
        PracticeSession session = createUnfinishedSession();
        List<PracticeLog> logs = List.of(createLog(session, 1L, poolItems.getFirst().getId()));

        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);
        when(practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(100L)).thenReturn(logs);
        when(quizQuestionPoolApi.findAllById(anyCollection())).thenReturn(poolItems.subList(0, 1));

        PracticeSessionDTO resumed = practiceQuizService.getUnfinishedSession(course.getId(), 100L, student);

        assertThat(resumed.id()).isEqualTo(100L);
        assertThat(resumed.questions()).hasSize(1);
        assertThat(resumed.questions().getFirst().explanation()).isNull();
    }

    @Test
    void testGetUnfinishedSessionRejectsCompletedSession() {
        PracticeSession session = createUnfinishedSession();
        session.setCompletedAt(ZonedDateTime.now());
        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);

        assertThatThrownBy(() -> practiceQuizService.getUnfinishedSession(course.getId(), 100L, student)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("already been completed");
    }

    private PracticeSession createUnfinishedSession() {
        PracticeSession session = new PracticeSession();
        session.setId(100L);
        session.setUser(student);
        session.setCourse(course);
        session.setStartTime(ZonedDateTime.now().minusMinutes(10));
        return session;
    }

    private static PracticeLog createLog(PracticeSession session, long id, long poolItemId) {
        PracticeLog log = new PracticeLog();
        log.setId(id);
        log.setPracticeSession(session);
        log.setPoolItemId(poolItemId);
        return log;
    }

    @Test
    void testSubmitAlreadyCompletedSessionThrowsBadRequest() {
        PracticeSession session = new PracticeSession();
        session.setId(100L);
        session.setUser(student);
        session.setCourse(course);
        session.setCompletedAt(ZonedDateTime.now());

        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);

        assertThatThrownBy(() -> practiceQuizService.submitPracticeSession(course.getId(), 100L, student, new PracticeSubmissionDTO(List.of())))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("already been completed");

        verify(practiceLogRepository, never()).saveAll(anyList());
    }

    @Test
    void testReviewUnfinishedSessionThrowsBadRequest() {
        PracticeSession session = new PracticeSession();
        session.setId(100L);
        session.setUser(student);
        session.setCourse(course);
        session.setCompletedAt(null);

        when(practiceSessionRepository.findByIdAndCourseIdAndUserIdOrElseThrow(100L, course.getId(), student.getId())).thenReturn(session);

        assertThatThrownBy(() -> practiceQuizService.getPracticeSessionForReview(course.getId(), 100L, student)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("not completed yet");
    }

    @Test
    void testGetPracticeSessionsForStudent() {
        PracticeSession session = new PracticeSession();
        session.setId(100L);
        session.setUser(student);
        session.setCourse(course);
        session.setStartTime(ZonedDateTime.now());
        session.setCompletedAt(ZonedDateTime.now());
        session.setScore(8.0);
        session.setMaxPoints(10.0);

        when(practiceSessionRepository.findAllByUserIdAndCourseIdOrderByStartTimeDesc(student.getId(), course.getId())).thenReturn(List.of(session));

        List<PracticeSessionSummaryDTO> summaries = practiceQuizService.getPracticeSessionsForStudent(course.getId(), student);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).id()).isEqualTo(100L);
        assertThat(summaries.get(0).score()).isEqualTo(8.0);
        assertThat(summaries.get(0).completed()).isTrue();
    }
}
