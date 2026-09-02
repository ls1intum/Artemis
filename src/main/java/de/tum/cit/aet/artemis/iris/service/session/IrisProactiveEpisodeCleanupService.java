package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;

/**
 * Retention for the proactive episode registry. Every accepted struggle trigger registers an episode row so the
 * paths that read the terminal state and then write can serialize on it. A run whose callback never arrives leaves
 * that row open, and nothing on the request path would ever remove it, so without this the table grows with every
 * trigger that was never closed.
 *
 * <p>
 * Only rows without an outcome are removed. A terminal outcome is what suppresses a late message for an episode, so
 * deleting one would resurrect the very race the registry exists to close.
 */
@Lazy
@Service
@Profile(PROFILE_SCHEDULING)
public class IrisProactiveEpisodeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IrisProactiveEpisodeCleanupService.class);

    /**
     * How long an open episode is kept. Orders of magnitude above {@code artemis.iris.jobs.timeout} (300 s by
     * default), which bounds how long a run can still produce a callback: an episode open for a week has no live job
     * behind it, so removing it cannot race a write. Deleting one early would cost nothing durable either, since an
     * open row carries no outcome and a later write re-registers the episode.
     */
    private static final Duration OPEN_EPISODE_RETENTION = Duration.ofDays(7);

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    public IrisProactiveEpisodeCleanupService(IrisProactiveEpisodeRepository irisProactiveEpisodeRepository) {
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
    }

    /**
     * Removes proactive episodes that never reached a terminal outcome and are older than
     * {@link #OPEN_EPISODE_RETENTION}. Runs nightly on the scheduling node.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupOpenProactiveEpisodes() {
        int deleted = irisProactiveEpisodeRepository.deleteOpenEpisodesOlderThan(ZonedDateTime.now().minus(OPEN_EPISODE_RETENTION));
        if (deleted > 0) {
            log.info("Deleted {} proactive episodes that stayed open for more than {} days", deleted, OPEN_EPISODE_RETENTION.toDays());
        }
    }
}
