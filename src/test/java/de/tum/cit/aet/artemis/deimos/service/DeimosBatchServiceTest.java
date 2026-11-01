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
import java.util.concurrent.Executor;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.deimos.config.DeimosLimits;
import de.tum.cit.aet.artemis.deimos.dto.DeimosAnalysisCompleteEmailDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchScope;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosExerciseScopeInfoDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailedAnalysisDetail;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosMaliciousParticipationLink;
import de.tum.cit.aet.artemis.deimos.dto.DeimosManipulationFinding;
import de.tum.cit.aet.artemis.deimos.dto.DeimosOmission;
import de.tum.cit.aet.artemis.deimos.dto.DeimosOmissionReason;
import de.tum.cit.aet.artemis.deimos.dto.DeimosTriggerType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosVerdict;
import de.tum.cit.aet.artemis.deimos.repository.DeimosBatchParticipationRepository;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

class DeimosBatchServiceTest {

    private static final long PARTICIPATION_LIMIT = 5000L;

    private static final ZonedDateTime FIXED_NOW = ZonedDateTime.parse("2026-01-15T12:00:00Z");

    private DeimosBatchParticipationRepository deimosBatchParticipationRepository;

    private DeimosAnalysisService deimosAnalysisService;

    private MailSendingService mailSendingService;

    private CourseRepository courseRepository;

    private ProgrammingExerciseRepository programmingExerciseRepository;

    private DeimosBatchService deimosBatchService;

    @BeforeEach
    void setUp() throws MalformedURLException {
        deimosBatchParticipationRepository = Mockito.mock(DeimosBatchParticipationRepository.class);
        deimosAnalysisService = Mockito.mock(DeimosAnalysisService.class);
        mailSendingService = Mockito.mock(MailSendingService.class);
        courseRepository = Mockito.mock(CourseRepository.class);
        programmingExerciseRepository = Mockito.mock(ProgrammingExerciseRepository.class);
        deimosBatchService = createService(Runnable::run, DeimosLimits.DEFAULT_MAX_PARTICIPATIONS_PER_RUN, DeimosLimits.DEFAULT_MAX_WINDOW_DAYS);
    }

    private DeimosBatchService createService(Executor executor, long maxParticipationsPerRun, long maxWindowDays) throws MalformedURLException {
        return new DeimosBatchService(deimosBatchParticipationRepository, deimosAnalysisService, mailSendingService, courseRepository, programmingExerciseRepository,
                URI.create("http://localhost:8080").toURL(), executor, maxParticipationsPerRun, maxWindowDays);
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

        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(7L, from, to)).thenReturn(2L);
        when(deimosBatchParticipationRepository.findParticipationIdsForCourseInRange(eq(7L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(101L, 102L)));
        when(courseRepository.getCourseTitle(7L)).thenReturn("Course 7");
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.COURSE), eq(from), eq(to), eq(List.of(101L, 102L))))
                .thenReturn(new DeimosBatchSummaryDTO("run-1", "MANUAL", "COURSE", from, to, 2, 2, 0, 1, 0, 1, 0,
                        List.of(analysis(101L, DeimosVerdict.MALICIOUS, true, "bad"), analysis(102L, DeimosVerdict.BENIGN, false, "ok")), List.of()));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerCourseBatch(7L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        var recipientCaptor = ArgumentCaptor.forClass(MailRecipientDTO.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
        verify(mailSendingService).buildAndSendAsync(recipientCaptor.capture(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"),
                contextCaptor.capture());
        assertThat(recipientCaptor.getValue()).isEqualTo(MailRecipientDTO.from(triggerUser));
        var emailData = (DeimosAnalysisCompleteEmailDTO) contextCaptor.getValue().get("analysis");
        assertThat(emailData).isNotNull();
        assertThat(emailData.courseTitle()).isEqualTo("Course 7");
        assertThat(emailData.analyzed()).isEqualTo(2L);
        assertThat(emailData.maliciousParticipationLinks()).hasSize(1);
        assertThat(emailData.maliciousParticipationLinks().getFirst().participationId()).isEqualTo(101L);
        assertThat(emailData.maliciousParticipationLinks().getFirst().url())
                .isEqualTo("http://localhost:8080/course-management/7/programming-exercises/55/participations/101/submissions");
        assertThat(emailData.maliciousParticipationLinks().getFirst().rationale()).isEqualTo("bad");
    }

    @Test
    void triggerExerciseBatchRunsAnalysisAndSendsNotification() {
        ZonedDateTime from = FIXED_NOW.minusHours(8);
        ZonedDateTime to = FIXED_NOW;

        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForExerciseInRange(12L, from, to)).thenReturn(2L);
        when(deimosBatchParticipationRepository.findParticipationIdsForExerciseInRange(eq(12L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(201L, 202L)));
        when(programmingExerciseRepository.findDeimosExerciseScopeInfoById(12L))
                .thenReturn(java.util.Optional.of(new DeimosExerciseScopeInfoDTO(12L, "Exercise 12", 7L, "Course 7", null)));
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.EXERCISE), eq(from), eq(to), eq(List.of(201L, 202L))))
                .thenReturn(new DeimosBatchSummaryDTO("run-3", "MANUAL", "EXERCISE", from, to, 2, 2, 0, 1, 0, 1, 0, List.of(), List.of()));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerExerciseBatch(12L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        var recipientCaptor = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailSendingService).buildAndSendAsync(recipientCaptor.capture(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), any());
        assertThat(recipientCaptor.getValue()).isEqualTo(MailRecipientDTO.from(triggerUser));
    }

    @Test
    void triggerCourseBatchSendsFailureNotificationWhenAnalysisThrows() {
        ZonedDateTime from = FIXED_NOW.minusHours(8);
        ZonedDateTime to = FIXED_NOW;

        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(7L, from, to)).thenReturn(2L);
        when(deimosBatchParticipationRepository.findParticipationIdsForCourseInRange(eq(7L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(101L, 102L)));
        when(courseRepository.getCourseTitle(7L)).thenReturn("Course 7");
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.COURSE), eq(from), eq(to), eq(List.of(101L, 102L))))
                .thenThrow(new IllegalStateException("LLM unavailable"));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerCourseBatch(7L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), contextCaptor.capture());
        var emailData = (DeimosAnalysisCompleteEmailDTO) contextCaptor.getValue().get("analysis");
        assertThat(emailData.analyzed()).isEqualTo(0L);
        assertThat(emailData.failed()).isEqualTo(2L);
        assertThat(emailData.maliciousCount()).isEqualTo(0L);
        assertThat(emailData.failedAnalysisDetails()).hasSize(1);
        assertThat(emailData.failedAnalysisDetails().getFirst().participationId()).isEqualTo(DeimosBatchSummaryDTO.BATCH_LEVEL_FAILURE_PARTICIPATION_ID);
        assertThat(emailData.failedAnalysisDetails().getFirst().reason()).contains("IllegalStateException").contains("LLM unavailable");
        assertThat(emailData.failedAnalysisDetails().getFirst().translationKey()).isEqualTo("email.deimos.analysisComplete.failureType.OTHER");
    }

    @Test
    void triggerCourseBatchRejectsParticipationLimitExceeded() {
        ZonedDateTime from = FIXED_NOW.minusDays(2);
        ZonedDateTime to = FIXED_NOW;
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(PARTICIPATION_LIMIT + 1);

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("participation limit");
    }

    @Test
    void triggerExerciseBatchRejectsParticipationLimitExceeded() {
        ZonedDateTime from = FIXED_NOW.minusDays(2);
        ZonedDateTime to = FIXED_NOW;
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForExerciseInRange(24L, from, to)).thenReturn(PARTICIPATION_LIMIT + 1);

        assertThatThrownBy(() -> deimosBatchService.triggerExerciseBatch(24L, new DeimosBatchRequestDTO(from, to), createTriggerUser()))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("participation limit");
    }

    @Test
    void triggerCourseBatchRejectsWhenQueueIsFull() throws MalformedURLException {
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(10L);

        var rejectingBatchService = createService(task -> {
            throw new RejectedExecutionException("queue full");
        }, DeimosLimits.DEFAULT_MAX_PARTICIPATIONS_PER_RUN, DeimosLimits.DEFAULT_MAX_WINDOW_DAYS);

        assertThatThrownBy(() -> rejectingBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser()))
                .isInstanceOf(ResponseStatusException.class).satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void triggerExerciseBatchRejectsWhenQueueIsFull() throws MalformedURLException {
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForExerciseInRange(24L, from, to)).thenReturn(10L);

        var rejectingBatchService = createService(task -> {
            throw new RejectedExecutionException("queue full");
        }, DeimosLimits.DEFAULT_MAX_PARTICIPATIONS_PER_RUN, DeimosLimits.DEFAULT_MAX_WINDOW_DAYS);

        assertThatThrownBy(() -> rejectingBatchService.triggerExerciseBatch(24L, new DeimosBatchRequestDTO(from, to), createTriggerUser()))
                .isInstanceOf(ResponseStatusException.class).satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void triggerCourseBatchSendsFailureNotificationWhenCollectionExceedsRuntimeLimit() {
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(PARTICIPATION_LIMIT);
        when(deimosBatchParticipationRepository.findParticipationIdsForCourseInRange(eq(42L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(LongStream.rangeClosed(1, PARTICIPATION_LIMIT + 1).boxed().toList()));
        when(courseRepository.getCourseTitle(42L)).thenReturn("Course 42");

        var response = deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser());

        assertThat(response.status()).isEqualTo("ACCEPTED");
        verify(deimosAnalysisService, Mockito.never()).analyze(any(), any(), any(), any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), contextCaptor.capture());
        var emailData = (DeimosAnalysisCompleteEmailDTO) contextCaptor.getValue().get("analysis");
        assertThat(emailData.analyzed()).isEqualTo(0L);
        // The collection aborts before the candidate list is assigned, so the count observed while collecting is what
        // the email has to report. Reporting one failure instead would tell the instructor a single participation went
        // wrong, when in fact none of more than five thousand of them was examined.
        assertThat(emailData.failed()).isEqualTo(PARTICIPATION_LIMIT + 1);
        assertThat(emailData.failedAnalysisDetails()).hasSize(1);
        assertThat(emailData.failedAnalysisDetails().getFirst().reason()).contains("exceeded");
    }

    @Test
    void notificationListsEveryReportedVerdictAndTheCategoryOfEveryFailure() {
        ZonedDateTime from = FIXED_NOW.minusDays(1);
        ZonedDateTime to = FIXED_NOW;
        var finding = new DeimosManipulationFinding("src/Attack.java", 3, "abcdef12, 2026-01-15T10:00Z");
        var omission = new DeimosOmission(DeimosOmissionReason.FILE_INPUT_LIMIT, "src/Big.java", "abcdef12, 2026-01-15T10:00Z");
        var manipulation = new DeimosBatchSummaryDTO.ParticipationAnalysis(301L, 55L, DeimosVerdict.MANIPULATION_ATTEMPT, null, null, DeimosFailureType.LLM_TIMEOUT,
                List.of(finding), 4, List.of(), 0);
        var incomplete = new DeimosBatchSummaryDTO.ParticipationAnalysis(303L, 55L, DeimosVerdict.INCOMPLETELY_CHECKED, false, "looks fine", null, List.of(), 0, List.of(omission),
                12);
        var failures = List.of(new DeimosBatchSummaryDTO.FailedAnalysis(305L, DeimosFailureType.LLM_AUTHENTICATION_FAILED, "rejected"),
                new DeimosBatchSummaryDTO.FailedAnalysis(DeimosBatchSummaryDTO.BATCH_LEVEL_FAILURE_PARTICIPATION_ID, DeimosFailureType.LLM_AUTHENTICATION_FAILED, "aborted"));
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(7L, from, to)).thenReturn(7L);
        when(deimosBatchParticipationRepository.findParticipationIdsForCourseInRange(eq(7L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(301L, 302L, 303L, 304L, 305L, 306L, 307L)));
        when(courseRepository.getCourseTitle(7L)).thenReturn("Course 7");
        when(deimosAnalysisService.analyze(any(), any(), any(), any(), any(), any())).thenReturn(new DeimosBatchSummaryDTO("run-9", "MANUAL", "COURSE", from, to, 7, 4, 1, 1, 1, 1,
                3, List.of(manipulation, analysis(302L, DeimosVerdict.MALICIOUS, true, "bad"), incomplete, analysis(304L, DeimosVerdict.BENIGN, false, "ok")), failures));

        deimosBatchService.triggerCourseBatch(7L, new DeimosBatchRequestDTO(from, to), createTriggerUser());

        var emailData = captureEmailData();
        assertThat(emailData.manipulationAttemptCount()).isEqualTo(1);
        assertThat(emailData.incompletelyCheckedCount()).isEqualTo(1);
        assertThat(emailData.manipulationAttemptLinks()).singleElement().satisfies(link -> {
            assertThat(link.participationId()).isEqualTo(301L);
            assertThat(link.findings()).containsExactly(finding);
            assertThat(link.findingCount()).isEqualTo(4);
            assertThat(link.llmMalicious()).isNull();
            assertThat(link.rationale()).isNull();
            assertThat(link.failureTranslationKey()).isEqualTo("email.deimos.analysisComplete.failureType.LLM_TIMEOUT");
        });
        assertThat(emailData.maliciousParticipationLinks()).extracting(DeimosMaliciousParticipationLink::participationId).containsExactly(302L);
        assertThat(emailData.incompletelyCheckedLinks()).singleElement().satisfies(link -> {
            assertThat(link.participationId()).isEqualTo(303L);
            assertThat(link.omissions()).containsExactly(omission);
            assertThat(link.omissionCount()).isEqualTo(12);
            assertThat(link.rationale()).isEqualTo("looks fine");
        });
        // A benign participation is counted but never listed.
        assertThat(emailData.benignCount()).isEqualTo(1);
        assertThat(emailData.failedAnalysisDetails()).extracting(DeimosFailedAnalysisDetail::translationKey)
                .containsOnly("email.deimos.analysisComplete.failureType.LLM_AUTHENTICATION_FAILED");
        // The batch-level entry stands for the participations the aborted run never reached.
        assertThat(emailData.failureBreakdown()).singleElement().satisfies(entry -> {
            assertThat(entry.failureType()).isEqualTo(DeimosFailureType.LLM_AUTHENTICATION_FAILED);
            assertThat(entry.count()).isEqualTo(3);
        });
    }

    @Test
    void configuredLimitsReplaceTheDefaults() throws MalformedURLException {
        var restrictedService = createService(Runnable::run, 10, 7);
        ZonedDateTime to = FIXED_NOW;

        assertThatThrownBy(() -> restrictedService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(to.minusDays(7).minusSeconds(1), to), createTriggerUser()))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("window");

        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(42L, to.minusDays(7), to)).thenReturn(11L);
        assertThatThrownBy(() -> restrictedService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(to.minusDays(7), to), createTriggerUser()))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("participation limit");
    }

    @Test
    void configuredParticipationLimitAppliesWhileCollecting() throws MalformedURLException {
        var restrictedService = createService(Runnable::run, 10, DeimosLimits.DEFAULT_MAX_WINDOW_DAYS);
        ZonedDateTime from = FIXED_NOW.minusHours(2);
        ZonedDateTime to = FIXED_NOW;
        when(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(42L, from, to)).thenReturn(10L);
        when(deimosBatchParticipationRepository.findParticipationIdsForCourseInRange(eq(42L), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(LongStream.rangeClosed(1, 11).boxed().toList()));
        when(courseRepository.getCourseTitle(42L)).thenReturn("Course 42");

        restrictedService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser());

        verify(deimosAnalysisService, Mockito.never()).analyze(any(), any(), any(), any(), any(), any());
        var emailData = captureEmailData();
        assertThat(emailData.failed()).isEqualTo(11L);
        assertThat(emailData.failedAnalysisDetails().getFirst().reason()).contains("exceeded 10");
    }

    private DeimosAnalysisCompleteEmailDTO captureEmailData() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> contextCaptor = ArgumentCaptor.forClass((Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
        verify(mailSendingService).buildAndSendAsync(any(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), contextCaptor.capture());
        return (DeimosAnalysisCompleteEmailDTO) contextCaptor.getValue().get("analysis");
    }

    private static DeimosBatchSummaryDTO.ParticipationAnalysis analysis(long participationId, DeimosVerdict verdict, boolean malicious, String rationale) {
        return new DeimosBatchSummaryDTO.ParticipationAnalysis(participationId, 55L, verdict, malicious, rationale, null, List.of(), 0, List.of(), 0);
    }

    private static User createTriggerUser() {
        User user = new User();
        user.setLogin("instructor1");
        user.setEmail("instructor1@example.org");
        user.setLangKey("en");
        return user;
    }
}
