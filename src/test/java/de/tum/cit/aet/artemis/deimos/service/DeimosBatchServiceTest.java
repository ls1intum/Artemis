package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosExerciseScopeInfoDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosMaliciousParticipationLink;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

class DeimosBatchServiceTest {

    private static final long PARTICIPATION_LIMIT = 5000L;

    private static final ZonedDateTime FIXED_NOW = ZonedDateTime.parse("2026-01-15T12:00:00Z");

    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    private DeimosAnalysisService deimosAnalysisService;

    private MailSendingService mailSendingService;

    private CourseRepository courseRepository;

    private ProgrammingExerciseRepository programmingExerciseRepository;

    private DeimosBatchService deimosBatchService;

    @BeforeEach
    void setUp() throws MalformedURLException {
        programmingSubmissionRepository = Mockito.mock(ProgrammingSubmissionRepository.class);
        deimosAnalysisService = Mockito.mock(DeimosAnalysisService.class);
        mailSendingService = Mockito.mock(MailSendingService.class);
        courseRepository = Mockito.mock(CourseRepository.class);
        programmingExerciseRepository = Mockito.mock(ProgrammingExerciseRepository.class);
        deimosBatchService = new DeimosBatchService(programmingSubmissionRepository, deimosAnalysisService, mailSendingService, courseRepository, programmingExerciseRepository,
                URI.create("http://localhost:8080").toURL(), r -> r.run());
    }

    @Test
    void triggerCourseBatchRejectsInvalidRange() {
        ZonedDateTime from = FIXED_NOW;
        ZonedDateTime to = from.minusHours(1);

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void triggerCourseBatchRejectsOversizedWindow() {
        ZonedDateTime from = FIXED_NOW.minusDays(40);
        ZonedDateTime to = FIXED_NOW;

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("configured maximum");
    }

    @Test
    void triggerCourseBatchRejectsWindowSlightlyAboveMaximum() {
        ZonedDateTime from = FIXED_NOW.minusDays(31).minusSeconds(1);
        ZonedDateTime to = FIXED_NOW;

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("configured maximum");
    }

    @Test
    void triggerCourseBatchRunsAnalysisAndSendsNotification() {
        ZonedDateTime from = FIXED_NOW.minusDays(1);
        ZonedDateTime to = FIXED_NOW;

        when(programmingSubmissionRepository.countDistinctParticipationIdsForCourseInRange(7L, from, to)).thenReturn(2L);
        when(programmingSubmissionRepository.findParticipationIdsForCourseInRange(eq(7L), eq(from), eq(to), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(101L, 102L)));
        when(courseRepository.getCourseTitle(7L)).thenReturn("Course 7");
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.COURSE), eq(from), eq(to), eq(List.of(101L, 102L))))
                .thenReturn(new DeimosBatchSummaryDTO("run-1", "MANUAL", "COURSE", from, to, 2, 2, 1, 1, 0,
                        List.of(new DeimosBatchSummaryDTO.ParticipationAnalysis(101L, 55L, true, "bad"), new DeimosBatchSummaryDTO.ParticipationAnalysis(102L, 55L, false, "ok"))));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerCourseBatch(7L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        var userCaptor = ArgumentCaptor.forClass(User.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(mailSendingService).buildAndSendAsync(userCaptor.capture(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"),
                contextCaptor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(triggerUser);
        @SuppressWarnings("unchecked")
        List<DeimosMaliciousParticipationLink> links = (List<DeimosMaliciousParticipationLink>) contextCaptor.getValue().get("maliciousParticipationLinks");
        assertThat(links).hasSize(1);
        assertThat(links.getFirst().participationId()).isEqualTo(101L);
        assertThat(links.getFirst().url()).isEqualTo("http://localhost:8080/course-management/7/programming-exercises/55/participations/101/submissions");
        assertThat(links.getFirst().rationale()).isEqualTo("bad");
    }

    @Test
    void triggerExerciseBatchRunsAnalysisAndSendsNotification() {
        ZonedDateTime from = FIXED_NOW.minusHours(8);
        ZonedDateTime to = FIXED_NOW;

        when(programmingSubmissionRepository.countDistinctParticipationIdsForExerciseInRange(12L, from, to)).thenReturn(2L);
        when(programmingSubmissionRepository.findParticipationIdsForExerciseInRange(eq(12L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(201L, 202L)));
        when(programmingExerciseRepository.findDeimosExerciseScopeInfoById(12L))
                .thenReturn(java.util.Optional.of(new DeimosExerciseScopeInfoDTO(12L, "Exercise 12", 7L, "Course 7", null)));
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.EXERCISE), eq(from), eq(to), eq(List.of(201L, 202L))))
                .thenReturn(new DeimosBatchSummaryDTO("run-3", "MANUAL", "EXERCISE", from, to, 2, 2, 1, 1, 0, List.of()));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerExerciseBatch(12L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        var userCaptor = ArgumentCaptor.forClass(User.class);
        verify(mailSendingService).buildAndSendAsync(userCaptor.capture(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), any());
        assertThat(userCaptor.getValue()).isEqualTo(triggerUser);
    }

    @Test
    void triggerCourseBatchSendsFailureNotificationWhenAnalysisThrows() {
        ZonedDateTime from = FIXED_NOW.minusHours(8);
        ZonedDateTime to = FIXED_NOW;

        when(programmingSubmissionRepository.countDistinctParticipationIdsForCourseInRange(7L, from, to)).thenReturn(2L);
        when(programmingSubmissionRepository.findParticipationIdsForCourseInRange(eq(7L), eq(from), eq(to), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(101L, 102L)));
        when(courseRepository.getCourseTitle(7L)).thenReturn("Course 7");
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.COURSE), eq(from), eq(to), eq(List.of(101L, 102L))))
                .thenThrow(new IllegalStateException("LLM unavailable"));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerCourseBatch(7L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().get("analyzed")).isEqualTo(0L);
        assertThat(contextCaptor.getValue().get("failed")).isEqualTo(2L);
        assertThat(contextCaptor.getValue().get("maliciousCount")).isEqualTo(0L);
    }

    @Test
    void triggerCourseBatchRejectsParticipationLimitExceeded() {
        ZonedDateTime from = FIXED_NOW.minusDays(2);
        ZonedDateTime to = FIXED_NOW;
        when(programmingSubmissionRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(PARTICIPATION_LIMIT + 1);

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("participation limit");
    }

    @Test
    void triggerExerciseBatchRejectsParticipationLimitExceeded() {
        ZonedDateTime from = FIXED_NOW.minusDays(2);
        ZonedDateTime to = FIXED_NOW;
        when(programmingSubmissionRepository.countDistinctParticipationIdsForExerciseInRange(24L, from, to)).thenReturn(PARTICIPATION_LIMIT + 1);

        assertThatThrownBy(() -> deimosBatchService.triggerExerciseBatch(24L, new DeimosBatchRequestDTO(from, to), createTriggerUser()))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("participation limit");
    }

    @Test
    void triggerCourseBatchRejectsWhenQueueIsFull() throws MalformedURLException {
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(programmingSubmissionRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(10L);

        var rejectingBatchService = new DeimosBatchService(programmingSubmissionRepository, deimosAnalysisService, mailSendingService, courseRepository,
                programmingExerciseRepository, URI.create("http://localhost:8080").toURL(), task -> {
                    throw new RejectedExecutionException("queue full");
                });

        assertThatThrownBy(() -> rejectingBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser()))
                .isInstanceOf(ResponseStatusException.class).satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void triggerExerciseBatchRejectsWhenQueueIsFull() throws MalformedURLException {
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(programmingSubmissionRepository.countDistinctParticipationIdsForExerciseInRange(24L, from, to)).thenReturn(10L);

        var rejectingBatchService = new DeimosBatchService(programmingSubmissionRepository, deimosAnalysisService, mailSendingService, courseRepository,
                programmingExerciseRepository, URI.create("http://localhost:8080").toURL(), task -> {
                    throw new RejectedExecutionException("queue full");
                });

        assertThatThrownBy(() -> rejectingBatchService.triggerExerciseBatch(24L, new DeimosBatchRequestDTO(from, to), createTriggerUser()))
                .isInstanceOf(ResponseStatusException.class).satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void triggerCourseBatchSendsFailureNotificationWhenCollectionExceedsRuntimeLimit() {
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(programmingSubmissionRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(PARTICIPATION_LIMIT);
        when(programmingSubmissionRepository.findParticipationIdsForCourseInRange(eq(42L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(LongStream.rangeClosed(1, PARTICIPATION_LIMIT + 1).boxed().toList()));
        when(courseRepository.getCourseTitle(42L)).thenReturn("Course 42");

        var response = deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser());

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(deimosAnalysisService, Mockito.never()).analyze(any(), any(), any(), any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().get("analyzed")).isEqualTo(0L);
        assertThat(contextCaptor.getValue().get("failed")).isEqualTo(1L);
    }

    private static User createTriggerUser() {
        User user = new User();
        user.setLogin("instructor1");
        user.setEmail("instructor1@example.org");
        user.setLangKey("en");
        return user;
    }
}
