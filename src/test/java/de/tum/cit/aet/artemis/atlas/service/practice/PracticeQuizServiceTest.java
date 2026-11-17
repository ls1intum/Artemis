package de.tum.cit.aet.artemis.atlas.service.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
import de.tum.cit.aet.artemis.quiz.api.QuizQuestionApi;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
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
    private QuizQuestionApi quizQuestionApi;

    private PracticeAllocationPolicy practiceAllocationPolicy;

    private JsonMapper jsonMapper;

    private PracticeQuizService practiceQuizService;

    private User student;

    private Course course;

    private List<QuizQuestion> poolQuestions;

    @BeforeEach
    void setUp() {
        practiceAllocationPolicy = new UniformBaselinePolicy();
        jsonMapper = new JsonMapper();
        practiceQuizService = new PracticeQuizService(practiceSessionRepository, practiceLogRepository, courseCompetencyRepository, quizQuestionApi, practiceAllocationPolicy,
                jsonMapper);

        student = new User();
        student.setId(10L);

        course = new Course();
        course.setId(20L);

        poolQuestions = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            MultipleChoiceQuestion question = new MultipleChoiceQuestion();
            question.setId(i);
            question.setTitle("Question " + i);
            question.setPoints(2.0);
            poolQuestions.add(question);
        }
    }

    @Test
    void testStartPracticeSessionEnforces5050ConditionSplit() {
        when(quizQuestionApi.findAllPracticeQuestionsForCourse(course.getId())).thenReturn(poolQuestions);
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

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PracticeLog>> logsCaptor = ArgumentCaptor.forClass(List.class);
        verify(practiceLogRepository).saveAll(logsCaptor.capture());
        List<PracticeLog> savedLogs = logsCaptor.getValue();

        assertThat(savedLogs).hasSize(10);
        long adaptiveCount = savedLogs.stream().filter(PracticeLog::isAdaptive).count();
        long controlCount = savedLogs.stream().filter(log -> !log.isAdaptive()).count();

        // 50/50 equal split verification
        assertThat(adaptiveCount).isEqualTo(5);
        assertThat(controlCount).isEqualTo(5);
    }

    @Test
    void testStartPracticeSessionThrowsWhenNoQuestionsAvailable() {
        when(quizQuestionApi.findAllPracticeQuestionsForCourse(course.getId())).thenReturn(List.of());

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
        log1.setQuizQuestion(poolQuestions.get(0));
        log1.setAdaptive(true);

        PracticeLog log2 = new PracticeLog();
        log2.setId(2L);
        log2.setPracticeSession(session);
        log2.setQuizQuestion(poolQuestions.get(1));
        log2.setAdaptive(false);

        List<PracticeLog> logs = List.of(log1, log2);

        when(practiceSessionRepository.findByIdAndUserIdOrElseThrow(100L, student.getId())).thenReturn(session);
        when(practiceLogRepository.findAllByPracticeSessionIdWithQuestion(100L)).thenReturn(logs);
        when(quizQuestionApi.evaluateSubmittedAnswer(eq(poolQuestions.get(0)), any(SubmittedAnswerFromLiveClientDTO.class))).thenReturn(2.0);
        when(quizQuestionApi.evaluateSubmittedAnswer(eq(poolQuestions.get(1)), any(SubmittedAnswerFromLiveClientDTO.class))).thenReturn(1.0);
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubmittedAnswerFromLiveClientDTO answer1 = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolQuestions.get(0).getId()), Set.of());
        SubmittedAnswerFromLiveClientDTO answer2 = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolQuestions.get(1).getId()), Set.of());
        PracticeSubmissionDTO submission = new PracticeSubmissionDTO(List.of(answer1, answer2));

        PracticeSessionReviewDTO review = practiceQuizService.submitPracticeSession(100L, student, submission);

        assertThat(review).isNotNull();
        assertThat(review.score()).isEqualTo(3.0);
        assertThat(review.maxPoints()).isEqualTo(4.0);
        assertThat(review.completedAt()).isNotNull();
        assertThat(review.questions()).hasSize(2);

        verify(practiceLogRepository).saveAll(logs);
        verify(practiceSessionRepository).save(session);
        assertThat(log1.getScore()).isEqualTo(2.0);
        assertThat(log2.getScore()).isEqualTo(1.0);
    }

    @Test
    void testSubmitAlreadyCompletedSessionThrowsBadRequest() {
        PracticeSession session = new PracticeSession();
        session.setId(100L);
        session.setUser(student);
        session.setCourse(course);
        session.setCompletedAt(ZonedDateTime.now());

        when(practiceSessionRepository.findByIdAndUserIdOrElseThrow(100L, student.getId())).thenReturn(session);

        assertThatThrownBy(() -> practiceQuizService.submitPracticeSession(100L, student, new PracticeSubmissionDTO(List.of()))).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("already been completed");

        verify(practiceLogRepository, never()).saveAll(anyList());
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
        when(practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(100L)).thenReturn(List.of(new PracticeLog(), new PracticeLog()));

        List<PracticeSessionSummaryDTO> summaries = practiceQuizService.getPracticeSessionsForStudent(course.getId(), student);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).id()).isEqualTo(100L);
        assertThat(summaries.get(0).score()).isEqualTo(8.0);
        assertThat(summaries.get(0).totalQuestions()).isEqualTo(2);
        assertThat(summaries.get(0).completed()).isTrue();
    }
}
