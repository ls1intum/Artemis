package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Scheduled service that computes and sends the daily Iris usage digest email.
 * <p>
 * The digest covers the previous calendar day (UTC midnight to midnight) and is sent
 * each morning. Guards prevent execution in dev, test-server, and scheduling-inactive profiles.
 * <p>
 * In a multi-node cluster, Hazelcast distributed locking ensures exactly one node sends the digest.
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

    private final HazelcastInstance hazelcastInstance;

    private final boolean isTestServer;

    @Nullable
    private IMap<String, Instant> scheduleStateMap;

    /**
     * Creates a new IrisUsageDigestScheduleService.
     *
     * @param profileService    used to check active Spring profiles
     * @param properties        Iris dashboard configuration properties
     * @param dashboardService  service that computes digest data
     * @param emailService      service that sends digest emails
     * @param isTestServer      whether the current instance is a test server
     * @param hazelcastInstance Hazelcast instance for distributed locking
     */
    public IrisUsageDigestScheduleService(ProfileService profileService, IrisDashboardProperties properties, IrisAdminDashboardService dashboardService,
            IrisDashboardEmailService emailService, @Value("${info.testServer:false}") boolean isTestServer, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.profileService = profileService;
        this.properties = properties;
        this.dashboardService = dashboardService;
        this.emailService = emailService;
        this.isTestServer = isTestServer;
        this.hazelcastInstance = hazelcastInstance;
    }

    private IMap<String, Instant> getScheduleStateMap() {
        if (scheduleStateMap == null) {
            scheduleStateMap = hazelcastInstance.getMap("iris-dashboard-schedule-state");
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
            IMap<String, Instant> stateMap = getScheduleStateMap();
            boolean locked = false;
            try {
                locked = stateMap.tryLock(digestKey, 5, TimeUnit.SECONDS);
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
                    stateMap.put(digestKey, TimeUtil.now().toInstant(), 3, TimeUnit.DAYS);
                }
                log.info("Iris digest sent to {} recipients", sent);
            }
            finally {
                if (locked) {
                    stateMap.unlock(digestKey);
                }
            }
        }
        catch (Exception e) {
            log.error("Failed to compute/send Iris digest", e);
        }
    }
}
