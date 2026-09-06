package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;

/**
 * Retention for the proactive episode registry. Every accepted struggle trigger registers an episode row so the
 * paths that read the terminal state and then write can serialize on it. A run whose callback never arrives leaves
 * that row open, and nothing on the request path would ever remove it, so without this the table grows with every
 * trigger that was never closed.
 *
 * <p>
 * Gated on {@link IrisEnabled} like the repository it depends on. Spring Boot excludes {@code @Scheduled} beans from
 * the global {@code spring.main.lazy-initialization}, so this one is created eagerly on a scheduling node; without
 * the condition it would ask for a repository bean that does not exist when Iris is off and fail startup there.
 *
 * <p>
 * Two kinds of row are kept. A terminal outcome is what suppresses a late message for an episode, so deleting one
 * would resurrect the very race the registry exists to close. A consumed ambient offer is what makes a repeated
 * reveal return the first reveal's message rather than write a second one, and what stops a spent offer from being
 * revealed again.
 *
 * <p>
 * An episode that is reaped and whose id the client later reuses comes back as a new lifecycle under the same
 * identity. Episode identity is {@code (user, exercise, episodeId)} with no generation, so that aliasing is a
 * property of the natural key rather than something retention introduces; a late outcome write for a reaped episode
 * reports {@code applied=false} and is intentionally discarded.
 */
@Lazy
@Service
@Profile(PROFILE_SCHEDULING)
@Conditional(IrisEnabled.class)
public class IrisProactiveEpisodeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(IrisProactiveEpisodeCleanupService.class);

    /**
     * How long an episode survives without a trigger. Orders of magnitude above {@code artemis.iris.jobs.timeout}
     * (300 s by default), which bounds how long a run can still produce a callback, and every trigger refreshes
     * {@code lastTriggeredAt}, so an episode that is still in use is never reaped out from under a run in flight.
     */
    private static final Duration ABANDONED_EPISODE_RETENTION = Duration.ofDays(7);

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    public IrisProactiveEpisodeCleanupService(IrisProactiveEpisodeRepository irisProactiveEpisodeRepository) {
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
    }

    /**
     * Removes proactive episodes that reached no terminal outcome, carry no revealed offer, and have not been
     * triggered for {@link #ABANDONED_EPISODE_RETENTION}. Runs nightly on the scheduling node.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupAbandonedProactiveEpisodes() {
        int deleted = irisProactiveEpisodeRepository.deleteAbandonedEpisodesLastTriggeredBefore(ZonedDateTime.now().minus(ABANDONED_EPISODE_RETENTION));
        if (deleted > 0) {
            log.info("Deleted {} proactive episodes without a trigger for more than {} days", deleted, ABANDONED_EPISODE_RETENTION.toDays());
        }
    }
}
