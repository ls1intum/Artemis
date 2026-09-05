package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.service.session.IrisProactiveEpisodeCleanupService;

/**
 * The retention job for the proactive episode registry. Without it every trigger whose callback never arrived would
 * leave a row behind forever, since no request path removes one.
 */
@ExtendWith(MockitoExtension.class)
class IrisProactiveEpisodeCleanupServiceTest {

    @Mock
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Test
    void deletesAbandonedEpisodesOlderThanTheRetentionWindow() {
        var service = new IrisProactiveEpisodeCleanupService(irisProactiveEpisodeRepository);
        var before = ZonedDateTime.now();

        service.cleanupAbandonedProactiveEpisodes();

        var cutoff = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(irisProactiveEpisodeRepository).deleteAbandonedEpisodesLastTriggeredBefore(cutoff.capture());
        // Seven days back, bounded on both sides so neither a shortened window (which could delete an episode whose
        // job can still call back) nor a disabled one goes unnoticed.
        assertThat(cutoff.getValue()).isBefore(before.minusDays(7).plusMinutes(1)).isAfter(before.minusDays(7).minusMinutes(1));
    }
}
