package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

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
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardAlertDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;

/**
 * Scheduled service that monitors Iris no-response rates and sends alert emails when thresholds are exceeded.
 * <p>
 * Uses a cooldown mechanism to avoid alert storms: after a successful send the service suppresses
 * further alerts for the configured cooldown period. Hazelcast distributed locking prevents
 * concurrent executions across cluster nodes.
 */
@Service
@Lazy(false)
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisUsageAlertService {

    private static final Logger log = LoggerFactory.getLogger(IrisUsageAlertService.class);

    private static final String SCHEDULE_STATE_MAP = "iris-dashboard-schedule-state";

    private static final String LAST_ALERT_SENT_AT_KEY = "last-alert-sent-at";

    private final ProfileService profileService;

    private final IrisDashboardProperties properties;

    private final IrisAdminDashboardService dashboardService;

    private final IrisDashboardEmailService emailService;

    private final IrisAdminDashboardRepository dashboardRepository;

    private final HazelcastInstance hazelcastInstance;

    private final boolean isTestServer;

    private boolean configValid = true;

    @Nullable
    private IMap<String, Instant> scheduleStateMap;

    /**
     * Creates a new IrisUsageAlertService.
     *
     * @param profileService      used to check active Spring profiles
     * @param properties          Iris dashboard configuration properties
     * @param dashboardService    service that computes overview metrics
     * @param emailService        service that sends alert emails
     * @param dashboardRepository repository for counting user messages
     * @param isTestServer        whether the current instance is a test server
     * @param hazelcastInstance   Hazelcast instance for distributed locking
     */
    public IrisUsageAlertService(ProfileService profileService, IrisDashboardProperties properties, IrisAdminDashboardService dashboardService,
            IrisDashboardEmailService emailService, IrisAdminDashboardRepository dashboardRepository, @Value("${info.testServer:false}") boolean isTestServer,
            @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.profileService = profileService;
        this.properties = properties;
        this.dashboardService = dashboardService;
        this.emailService = emailService;
        this.dashboardRepository = dashboardRepository;
        this.isTestServer = isTestServer;
        this.hazelcastInstance = hazelcastInstance;
    }

    private IMap<String, Instant> getScheduleStateMap() {
        if (scheduleStateMap == null) {
            scheduleStateMap = hazelcastInstance.getMap(SCHEDULE_STATE_MAP);
        }
        return scheduleStateMap;
    }

    /**
     * Validates the alert configuration at startup and sets {@code configValid = false} if any value is out of range.
     * When invalid, all future scheduled checks are skipped silently.
     */
    @PostConstruct
    public void validateConfig() {
        var alert = properties.getAlert();
        if (!alert.isEnabled()) {
            return;
        }
        if (alert.getCheckIntervalMinutes() <= 0) {
            log.error("Iris alert: check-interval-minutes must be > 0, got {}", alert.getCheckIntervalMinutes());
            configValid = false;
        }
        if (alert.getNoResponseRateThreshold() < 0 || alert.getNoResponseRateThreshold() > 100) {
            log.error("Iris alert: threshold must be in [0,100], got {}", alert.getNoResponseRateThreshold());
            configValid = false;
        }
        if (alert.getLookbackMinutes() <= properties.getStaleThresholdMinutes()) {
            log.error("Iris alert: lookback ({}) must be > stale threshold ({})", alert.getLookbackMinutes(), properties.getStaleThresholdMinutes());
            configValid = false;
        }
        if (alert.getCooldownMinutes() <= alert.getCheckIntervalMinutes()) {
            log.error("Iris alert: cooldown ({}) must be > check interval ({})", alert.getCooldownMinutes(), alert.getCheckIntervalMinutes());
            configValid = false;
        }
        if (alert.getLookbackMinutes() > properties.getMaxQueryWindowDays() * 1440L) {
            log.error("Iris alert: lookback ({}) must be <= maxQueryWindowDays * 1440 ({})", alert.getLookbackMinutes(), properties.getMaxQueryWindowDays() * 1440L);
            configValid = false;
        }
        if (alert.getMinimumEligibleSessions() <= 0 || alert.getMinimumUserMessages() <= 0) {
            log.error("Iris alert: minimumEligibleSessions and minimumUserMessages must be > 0");
            configValid = false;
        }
        if (alert.getCheckIntervalMinutes() > alert.getLookbackMinutes() - properties.getStaleThresholdMinutes()) {
            log.warn("Iris alert: check-interval ({}) > lookback-stale ({}) — may leave coverage gaps", alert.getCheckIntervalMinutes(),
                    alert.getLookbackMinutes() - properties.getStaleThresholdMinutes());
        }
    }

    /**
     * Periodically checks whether the Iris no-response rate exceeds the configured threshold and sends an alert email.
     * <p>
     * Skips execution when scheduling is inactive, the dev profile is active, the instance is a test server,
     * the alert feature is disabled, configuration is invalid, or the email service cannot send.
     * <p>
     * Uses Hazelcast distributed locking to prevent concurrent executions across cluster nodes
     * and to enforce the cooldown period cluster-wide.
     */
    @Scheduled(fixedRateString = "${artemis.iris.dashboard.alert.check-interval-minutes:30}", initialDelayString = "${artemis.iris.dashboard.alert.check-interval-minutes:30}", timeUnit = TimeUnit.MINUTES)
    public void checkAlertThresholds() {
        if (!properties.getAlert().isEnabled() || !configValid) {
            return;
        }
        if (!profileService.isSchedulingActive()) {
            return;
        }
        if (profileService.isDevActive()) {
            return;
        }
        if (isTestServer) {
            return;
        }
        if (!emailService.canSendAlert()) {
            return;
        }

        var now = TimeUtil.now().toInstant();
        var cooldown = Duration.ofMinutes(properties.getAlert().getCooldownMinutes());

        IMap<String, Instant> stateMap = getScheduleStateMap();
        boolean locked = false;
        try {
            locked = stateMap.tryLock(LAST_ALERT_SENT_AT_KEY, 5, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }

            Instant lastSent = stateMap.get(LAST_ALERT_SENT_AT_KEY);
            if (lastSent != null && now.minus(cooldown).isBefore(lastSent)) {
                return;
            }

            var alert = properties.getAlert();
            var lookbackStart = now.minus(Duration.ofMinutes(alert.getLookbackMinutes()));
            var staleBefore = dashboardService.computeStaleBefore(now, now);

            var overview = dashboardService.computeOverview(lookbackStart, staleBefore);

            if (overview.activeSessions() < alert.getMinimumEligibleSessions()) {
                return;
            }
            long userMessageCount = dashboardRepository.countUserMessages(lookbackStart, staleBefore);
            if (userMessageCount < alert.getMinimumUserMessages()) {
                return;
            }
            if (overview.noResponseRate() <= alert.getNoResponseRateThreshold()) {
                return;
            }

            var alertDto = new IrisDashboardAlertDTO(lookbackStart, staleBefore, overview.noResponseRate(), alert.getNoResponseRateThreshold(), overview.activeSessions(),
                    overview.noResponseSessionCount(), userMessageCount, List.of(), "/admin/iris-dashboard");

            int sent = emailService.sendAlert(alertDto);
            if (sent > 0) {
                stateMap.put(LAST_ALERT_SENT_AT_KEY, TimeUtil.now().toInstant());
                log.info("Iris alert sent to {} recipients (no-response rate: {}%)", sent, overview.noResponseRate());
            }
        }
        catch (Exception e) {
            log.error("Failed to compute/send Iris alert", e);
        }
        finally {
            if (locked) {
                stateMap.unlock(LAST_ALERT_SENT_AT_KEY);
            }
        }
    }
}
