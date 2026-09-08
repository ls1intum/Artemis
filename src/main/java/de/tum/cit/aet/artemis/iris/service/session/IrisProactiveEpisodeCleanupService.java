package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;

/**
 * Retention for the proactive episode registry. Every accepted struggle trigger registers an episode row so the
 * paths that read the terminal state and then write can serialize on it. A run whose callback never arrives leaves
 * that row open, and nothing on the request path would ever remove it, so without this the table grows with every
 * trigger that was never closed.
 *
 * <p>
 * Gated on {@link IrisEnabled} like the repository it depends on: without the condition it would ask for a repository
 * bean that does not exist when Iris is off and fail startup there. What instantiates it despite the {@code @Lazy}
 * below, and so registers the schedule, is {@code DeferredEagerBeanInitializer}, which forces every lazy singleton on
 * a core node after startup. Spring Boot's own exclusion for {@code @Scheduled} beans does not do it: that one only
 * defeats the global {@code spring.main.lazy-initialization} flag and leaves an explicit {@code @Lazy} standing.
 *
 * <p>
 * Two kinds of row are kept. A terminal outcome is what suppresses a late message for an episode, so deleting one
 * would resurrect the very race the registry exists to close. A consumed ambient offer is what makes a repeated
 * reveal return the first reveal's message rather than write a second one, and what stops a spent offer from being
 * revealed again.
 *
 * <p>
 * Those two kinds are not this job's business: they go with the course's student-data reset, which is where Artemis
 * already decides how long student data lives (within that reset's own scope, see
 * {@code IrisProactiveEpisodeRepository#deleteAllByCourseId}). This job only removes what no reset would ever have a
 * reason to keep.
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

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    private final IrisProactiveProperties proactiveProperties;

    public IrisProactiveEpisodeCleanupService(IrisProactiveEpisodeRepository irisProactiveEpisodeRepository, IrisProactiveProperties proactiveProperties) {
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
        this.proactiveProperties = proactiveProperties;
    }

    /**
     * Removes proactive episodes that reached no terminal outcome, carry no revealed offer, and have not been
     * triggered for {@code artemis.iris.proactive.abandoned-episode-retention}. Runs on the scheduling node.
     * <p>
     * The schedule is bound by placeholder rather than from {@link IrisProactiveProperties#getCleanupCron()},
     * because {@code @Scheduled} resolves its expression before any bean is available to read.
     */
    @Scheduled(cron = "${artemis.iris.proactive.cleanup-cron:0 30 3 * * *}")
    public void cleanupAbandonedProactiveEpisodes() {
        var retention = proactiveProperties.getAbandonedEpisodeRetention();
        int deleted = irisProactiveEpisodeRepository.deleteAbandonedEpisodesLastTriggeredBefore(ZonedDateTime.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} proactive episodes without a trigger for more than {}", deleted, retention);
        }
    }
}
