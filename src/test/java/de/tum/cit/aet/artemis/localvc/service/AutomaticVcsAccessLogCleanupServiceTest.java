package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsStaffRepositoryAccessLogRepository;

/**
 * The retention of the access log covers the log of the participations and the log of the tests and auxiliary repositories alike.
 */
class AutomaticVcsAccessLogCleanupServiceTest {

    @Test
    void deletesTheOutdatedEntriesOfBothLogsAndKeepsTheCutOffTheSame() {
        var participationLog = mock(VcsAccessLogRepository.class);
        var staffRepositoryLog = mock(VcsStaffRepositoryAccessLogRepository.class);
        var service = new AutomaticVcsAccessLogCleanupService(participationLog, staffRepositoryLog);
        ReflectionTestUtils.setField(service, "vcsAccessLogRetentionPeriod", 30);
        when(participationLog.findAllIdsBeforeDate(any())).thenReturn(Set.of(1L, 2L));
        when(staffRepositoryLog.findAllIdsBeforeDate(any())).thenReturn(Set.of(7L));

        service.cleanup();

        verify(participationLog).deleteAllById(Set.of(1L, 2L));
        verify(staffRepositoryLog).deleteAllById(Set.of(7L));
        var participationCutOff = ArgumentCaptor.forClass(ZonedDateTime.class);
        var staffRepositoryCutOff = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(participationLog).findAllIdsBeforeDate(participationCutOff.capture());
        verify(staffRepositoryLog).findAllIdsBeforeDate(staffRepositoryCutOff.capture());
        assertThat(staffRepositoryCutOff.getValue()).isEqualTo(participationCutOff.getValue());
        assertThat(participationCutOff.getValue()).isBefore(ZonedDateTime.now().minusDays(29)).isAfter(ZonedDateTime.now().minusDays(31));
    }
}
