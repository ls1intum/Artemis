package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Scheduled service that computes and sends the daily Iris usage digest email.
 * <p>
 * The digest covers the previous calendar day (UTC midnight to midnight) and is sent
 * each morning. Guards prevent execution in dev, test-server, and scheduling-inactive profiles.
 * <p>
 * In a multi-node cluster a Hazelcast lock plus a per-window {@code containsKey} marker keep this to a
 * single send under normal operation. Delivery is nonetheless <em>at-least-once</em>: if the lock holder
 * crashes after sending but before recording the marker, a node still waiting on the lock can resend; and
 * because the distributed lock is AP rather than a CP fenced lock, a network split-brain can also duplicate.
 * Both are accepted trade-offs for an internal admin email (see {@link IrisUsageAlertService} for the rationale).
 */
@Service
@Lazy(false)
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisUsageDigestScheduleService {

    private static final Logger log = LoggerFactory.getLogger(IrisUsageDigestScheduleService.class);

    private final ProfileService profileService;

    private final IrisDashboardProperties properties;

    private final IrisAdminDashboardService dashboardService;

    private final IrisDashboardEmailService emailService;

    private final DistributedDataProvider distributedDataProvider;

    private final boolean isTestServer;

    @Nullable
    /**
     * How long a "digest already sent" marker is kept. It only has to outlive the daily window it guards.
     */
    private static final Duration DIGEST_MARKER_TIME_TO_LIVE = Duration.ofDays(3);

    @Nullable
    private DistributedMap<String, Instant> scheduleStateMap;

    /**
     * Creates a new IrisUsageDigestScheduleService.
     *
     * @param profileService          used to check active Spring profiles
     * @param properties              Iris dashboard configuration properties
     * @param dashboardService        service that computes digest data
     * @param emailService            service that sends digest emails
     * @param isTestServer            whether the current instance is a test server
     * @param distributedDataProvider provider used for distributed state and locking
     */
    public IrisUsageDigestScheduleService(ProfileService profileService, IrisDashboardProperties properties, IrisAdminDashboardService dashboardService,
            IrisDashboardEmailService emailService, @Value("${info.testServer:false}") boolean isTestServer, DistributedDataProvider distributedDataProvider) {
        this.profileService = profileService;
        this.properties = properties;
        this.dashboardService = dashboardService;
        this.emailService = emailService;
        this.isTestServer = isTestServer;
        this.distributedDataProvider = distributedDataProvider;
    }

    private DistributedMap<String, Instant> getScheduleStateMap() {
        if (scheduleStateMap == null) {
            scheduleStateMap = distributedDataProvider.getExpiringMap("iris-dashboard-schedule-state", DIGEST_MARKER_TIME_TO_LIVE);
        }
        return scheduleStateMap;
    }

    /**
     * Sends the daily Iris usage digest email covering the previous calendar day.
     * <p>
     * Skips execution when scheduling is inactive, the dev profile is active, the instance
     * is a test server, the digest feature is disabled, or the email service cannot send.
     * <p>
     * Uses Hazelcast distributed locking to ensure only one node in the cluster sends the digest.
     */
    @Scheduled(cron = "${artemis.iris.dashboard.digest.cron:0 0 7 * * *}", zone = "UTC")
    public void sendDailyDigest() {
        if (!profileService.isSchedulingActive()) {
            return;
        }
        if (profileService.isDevActive()) {
            return;
        }
        if (isTestServer) {
            return;
        }
        if (!properties.getDigest().isEnabled()) {
            return;
        }
        if (!emailService.canSendDigest()) {
            return;
        }

        try {
            var now = TimeUtil.now();
            var windowEnd = now.withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC);
            var windowStart = windowEnd.minusDays(1);

            String digestKey = "digest-sent:" + windowStart.toInstant() + ":" + windowEnd.toInstant();
            DistributedMap<String, Instant> stateMap = getScheduleStateMap();
            DistributedLock digestLock = distributedDataProvider.getLock("iris-dashboard-schedule-state:" + digestKey);
            boolean locked = false;
            try {
                locked = digestLock.tryLock(Duration.ofSeconds(5));
                if (!locked) {
                    log.debug("Iris digest: could not acquire lock, another node is handling it");
                    return;
                }
                if (stateMap.containsKey(digestKey)) {
                    log.debug("Iris digest already sent for window {} to {}", windowStart, windowEnd);
                    return;
                }

                var staleBefore = dashboardService.computeStaleBefore(windowEnd.toInstant(), now.toInstant());
                var digest = dashboardService.computeDigestData(windowStart.toInstant(), windowEnd.toInstant(), staleBefore);
                int sent = emailService.sendDigest(digest);
                if (sent > 0) {
                    stateMap.put(digestKey, TimeUtil.now().toInstant());
                }
                log.info("Iris digest sent to {} recipients", sent);
            }
            finally {
                if (locked) {
                    digestLock.unlock();
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to compute/send Iris digest", e);
        }
    }
}
