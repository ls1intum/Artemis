package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.domain.VcsAnalyticsLog;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAnalyticsLogRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@ExtendWith(MockitoExtension.class)
class VcsAccessLogServiceTest {

    @Mock
    private VcsAccessLogRepository vcsAccessLogRepository;

    @Mock
    private VcsAnalyticsLogRepository vcsAnalyticsLogRepository;

    @InjectMocks
    private VcsAccessLogService vcsAccessLogService;

    private User testUser;

    private ProgrammingExerciseParticipation programmingParticipation;

    private Participation baseParticipation;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("student1");

        ReflectionTestUtils.setField(vcsAccessLogService, "analyticsSecretKey", "test-secure-key-for-vcs-analytics");

        ProgrammingExercise mockExercise = mock(ProgrammingExercise.class);
        lenient().when(mockExercise.getId()).thenReturn(42L);

        programmingParticipation = mock(ProgrammingExerciseStudentParticipation.class);
        lenient().when(programmingParticipation.getId()).thenReturn(12L);

        lenient().when(programmingParticipation.getExercise()).thenReturn(mockExercise);

        baseParticipation = mock(Participation.class);
        lenient().when(baseParticipation.getId()).thenReturn(12L);
        lenient().when(baseParticipation.getExercise()).thenReturn(mockExercise);
    }

    @Test
    void testSaveAccessLog_HTTPPath_shouldSaveToAnalytics() {
        when(vcsAnalyticsLogRepository.findCourseIdByParticipationId(12L)).thenReturn(Optional.of(10L));

        vcsAccessLogService.saveAccessLog(testUser, programmingParticipation, RepositoryActionType.PUSH, AuthenticationMechanism.PASSWORD, "commit123", "127.0.0.1");

        // Check that vcsAccessLogRepository tried to save the vcsAccessLog
        verify(vcsAccessLogRepository, times(1)).save(any(VcsAccessLog.class));

        // Check that vcsAnalyticsLogRepository tried to save the vcsAnalyticsLog
        verify(vcsAnalyticsLogRepository, times(1)).save(any(VcsAnalyticsLog.class));
    }

    @Test
    void testSaveVcsAccesslog_SSHPath_shouldSaveToAnalytics() {
        // Synthetic sshLog object from ssh
        VcsAccessLog sshLog = new VcsAccessLog(testUser, baseParticipation, "Student", "student@tum.de", RepositoryActionType.READ, AuthenticationMechanism.SSH, "commit789",
                "127.0.0.1");

        when(vcsAnalyticsLogRepository.findCourseIdByParticipationId(12L)).thenReturn(Optional.of(10L));

        vcsAccessLogService.saveVcsAccesslog(sshLog);

        // Check that sshLog is stored in vcsAccessLogRepository and vcsAnalyticsLogRepository
        verify(vcsAccessLogRepository, times(1)).save(sshLog);
        verify(vcsAnalyticsLogRepository, times(1)).save(any(VcsAnalyticsLog.class));
    }

    @Test
    void testUpdateRepositoryActionType_shouldUpdateBothTables() {
        // Imitate PULL -> CLONE update
        VcsAccessLog existingLog = new VcsAccessLog(testUser, baseParticipation, "name", "email", RepositoryActionType.PULL, AuthenticationMechanism.VCS_ACCESS_TOKEN, "commit789",
                "127.0.0.1");

        VcsAnalyticsLog existingAnalytics = new VcsAnalyticsLog();
        existingAnalytics.setRepositoryActionType(RepositoryActionType.PULL);

        LocalVCRepositoryUri repoUri = mock(LocalVCRepositoryUri.class);
        when(repoUri.toString()).thenReturn("http://localhost:8080/git/repo.git");

        when(vcsAccessLogRepository.findNewestByRepositoryUri(anyString())).thenReturn(Optional.of(existingLog));
        when(vcsAnalyticsLogRepository.findCourseIdByParticipationId(12L)).thenReturn(Optional.of(10L));
        when(vcsAnalyticsLogRepository.findLatestByMaskedUserIdAndExerciseId(anyString(), anyLong())).thenReturn(Optional.of(existingAnalytics));

        vcsAccessLogService.updateRepositoryActionType(repoUri, RepositoryActionType.CLONE);

        assertThat(existingLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(existingAnalytics.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);

        verify(vcsAccessLogRepository, times(1)).save(existingLog);
        verify(vcsAnalyticsLogRepository, times(1)).save(existingAnalytics);
    }
}
