package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
 * further alerts for the configured cooldown period. An {@link AtomicBoolean} guard prevents
 * concurrent executions of the check method.
 */
@Service
@Lazy
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisUsageAlertService {

    private static final Logger log = LoggerFactory.getLogger(IrisUsageAlertService.class);

    private final ProfileService profileService;

    private final IrisDashboardProperties properties;

    private final IrisAdminDashboardService dashboardService;

    private final IrisDashboardEmailService emailService;

    private final IrisAdminDashboardRepository dashboardRepository;

    private final boolean isTestServer;

    private boolean configValid = true;

    private final AtomicReference<Instant> lastAlertSentAt = new AtomicReference<>(Instant.EPOCH);

    private final AtomicBoolean alertInFlight = new AtomicBoolean(false);

    /**
     * Creates a new IrisUsageAlertService.
     *
     * @param profileService      used to check active Spring profiles
     * @param properties          Iris dashboard configuration properties
     * @param dashboardService    service that computes overview metrics
     * @param emailService        service that sends alert emails
     * @param dashboardRepository repository for counting user messages
     * @param isTestServer        whether the current instance is a test server
     */
    public IrisUsageAlertService(ProfileService profileService, IrisDashboardProperties properties, IrisAdminDashboardService dashboardService,
            IrisDashboardEmailService emailService, IrisAdminDashboardRepository dashboardRepository, @Value("${info.testServer:false}") boolean isTestServer) {
        this.profileService = profileService;
        this.properties = properties;
        this.dashboardService = dashboardService;
        this.emailService = emailService;
        this.dashboardRepository = dashboardRepository;
        this.isTestServer = isTestServer;
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
     * the alert feature is disabled, configuration is invalid, the email service cannot send, or the cooldown
     * has not yet elapsed since the last alert. Double-checked locking via {@link AtomicBoolean} prevents
     * concurrent executions.
     */
    @Scheduled(fixedRateString = "${artemis.iris.dashboard.alert.check-interval-minutes:30}", timeUnit = TimeUnit.MINUTES)
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
        if (now.minus(cooldown).isBefore(lastAlertSentAt.get())) {
            return;
        }

        if (!alertInFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            if (now.minus(cooldown).isBefore(lastAlertSentAt.get())) {
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
                lastAlertSentAt.set(TimeUtil.now().toInstant());
                log.info("Iris alert sent to {} recipients (no-response rate: {}%)", sent, overview.noResponseRate());
            }
        }
        catch (Exception e) {
            log.error("Failed to compute/send Iris alert", e);
        }
        finally {
            alertInFlight.set(false);
        }
    }
}
