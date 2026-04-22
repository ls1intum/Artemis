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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosExerciseScopeInfoDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

class DeimosBatchServiceTest {

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
        ZonedDateTime from = ZonedDateTime.now();
        ZonedDateTime to = from.minusHours(1);

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void triggerCourseBatchRejectsOversizedWindow() {
        ZonedDateTime from = ZonedDateTime.now().minusDays(40);
        ZonedDateTime to = ZonedDateTime.now();

        assertThatThrownBy(() -> deimosBatchService.triggerCourseBatch(42L, new DeimosBatchRequestDTO(from, to), createTriggerUser())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured maximum");
    }

    @Test
    void triggerCourseBatchRunsAnalysisAndSendsNotification() {
        ZonedDateTime from = ZonedDateTime.now().minusDays(1);
        ZonedDateTime to = ZonedDateTime.now();

        when(programmingSubmissionRepository.findParticipationIdsForCourseInRange(eq(7L), eq(from), eq(to), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(101L, 102L)));
        when(courseRepository.getCourseTitle(7L)).thenReturn("Course 7");
        when(deimosAnalysisService.analyze(any(), eq(DeimosTriggerType.MANUAL), eq(DeimosBatchScope.COURSE), eq(from), eq(to), eq(List.of(101L, 102L))))
                .thenReturn(new DeimosBatchSummaryDTO("run-1", "MANUAL", "COURSE", from, to, 2, 2, 0, 2, 0, List.of()));

        var triggerUser = createTriggerUser();
        var response = deimosBatchService.triggerCourseBatch(7L, new DeimosBatchRequestDTO(from, to), triggerUser);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        var userCaptor = ArgumentCaptor.forClass(User.class);
        verify(mailSendingService).buildAndSendAsync(userCaptor.capture(), eq("email.deimos.analysisComplete.title"), eq("mail/deimos/deimosAnalysisCompleteEmail"), any());
        assertThat(userCaptor.getValue()).isEqualTo(triggerUser);
    }

    @Test
    void triggerExerciseBatchRunsAnalysisAndSendsNotification() {
        ZonedDateTime from = ZonedDateTime.now().minusHours(8);
        ZonedDateTime to = ZonedDateTime.now();

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

    private static User createTriggerUser() {
        User user = new User();
        user.setLogin("instructor1");
        user.setEmail("instructor1@example.org");
        user.setLangKey("en");
        return user;
    }
}
