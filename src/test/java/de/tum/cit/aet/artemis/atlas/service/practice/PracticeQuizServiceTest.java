package de.tum.cit.aet.artemis.atlas.service.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    void testStartPracticeSessionEnforces5050ConditionSplit() {
        when(quizQuestionPoolApi.getPracticeEligibleQuestions(course.getId())).thenReturn(poolItems);
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
        when(quizQuestionPoolApi.getPracticeEligibleQuestions(course.getId())).thenReturn(List.of());

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

        when(practiceSessionRepository.findByIdAndUserIdOrElseThrow(100L, student.getId())).thenReturn(session);
        when(practiceLogRepository.findAllByPracticeSessionIdOrderByIdAsc(100L)).thenReturn(logs);
        when(quizQuestionPoolApi.findById(poolItems.get(0).getId())).thenReturn(Optional.of(poolItems.get(0)));
        when(quizQuestionPoolApi.findById(poolItems.get(1).getId())).thenReturn(Optional.of(poolItems.get(1)));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Answer 1: selected option 0 (correct) -> score 1.0
        SubmittedAnswerFromLiveClientDTO answer1 = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolItems.get(0).getId()), Set.of(new EntityIdRefDTO(0L)));
        // Answer 2: selected option 1 (incorrect) -> score 0.0
        SubmittedAnswerFromLiveClientDTO answer2 = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolItems.get(1).getId()), Set.of(new EntityIdRefDTO(1L)));
        PracticeSubmissionDTO submission = new PracticeSubmissionDTO(List.of(answer1, answer2));

        PracticeSessionReviewDTO review = practiceQuizService.submitPracticeSession(100L, student, submission);

        assertThat(review).isNotNull();
        assertThat(review.score()).isEqualTo(1.0);
        assertThat(review.maxPoints()).isEqualTo(2.0);
        assertThat(review.completedAt()).isNotNull();
        assertThat(review.questions()).hasSize(2);

        verify(practiceLogRepository).saveAll(logs);
        verify(practiceSessionRepository).save(session);
        assertThat(log1.getScore()).isEqualTo(1.0);
        assertThat(log2.getScore()).isEqualTo(0.0);
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
