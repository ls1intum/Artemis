package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.util.StringUtils;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;

@Lazy(false)
@Service
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisUsageAlertService {

    private static final Logger log = LoggerFactory.getLogger(IrisUsageAlertService.class);

    private static final String DASHBOARD_URL = "/admin/iris-dashboard";

    private static final String SCHEDULE_STATE_MAP = "iris-dashboard-schedule-state";

    private static final String LAST_ALERT_SENT_AT_KEY = "last-alert-sent-at";

    private final IrisAdminDashboardService dashboardService;

    private final IrisDashboardProperties properties;

    private final ProfileService profileService;

    private final MailSendingService mailSendingService;

    private final HazelcastInstance hazelcastInstance;

    @Nullable
    private IMap<String, Instant> scheduleStateMap;

    @Value("${info.contact:}")
    private String adminEmail;

    @Value("${info.testServer:false}")
    private boolean isTestServer;

    public IrisUsageAlertService(@Lazy IrisAdminDashboardService dashboardService, IrisDashboardProperties properties, ProfileService profileService,
            MailSendingService mailSendingService, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.dashboardService = dashboardService;
        this.properties = properties;
        this.profileService = profileService;
        this.mailSendingService = mailSendingService;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Checks the scheduled Iris no-response alert threshold when dashboard alerts are enabled.
     */
    @Scheduled(fixedRateString = "${artemis.iris.dashboard.alert.check-interval-minutes:30}", timeUnit = TimeUnit.MINUTES)
    public void checkAlertThresholds() {
        if (!profileService.isSchedulingActive() || profileService.isDevActive() || isTestServer || !properties.getAlert().isEnabled()) {
            return;
        }
        checkAndSendAlert();
    }

    /**
     * Sends an Iris no-response alert when the current lookback window exceeds the configured threshold and sample size.
     *
     * @return true if at least one alert email was scheduled
     */
    public boolean checkAndSendAlert() {
        var alert = properties.getAlert();
        Instant now = TimeUtil.now().toInstant();
        IMap<String, Instant> stateMap = getScheduleStateMap();
        stateMap.lock(LAST_ALERT_SENT_AT_KEY);
        try {
            Instant lastAlertSentAt = stateMap.get(LAST_ALERT_SENT_AT_KEY);
            if (lastAlertSentAt != null && Duration.between(lastAlertSentAt, now).compareTo(Duration.ofMinutes(alert.getCooldownMinutes())) < 0) {
                return false;
            }

            Instant from = now.minus(alert.getLookbackMinutes(), ChronoUnit.MINUTES);
            IrisDashboardOverviewDTO overview = dashboardService.getOverview(from, now, null);
            if (overview.activeSessions() < alert.getMinimumActiveSessions() || overview.userMessageCount() < alert.getMinimumUserMessages()
                    || overview.noResponseRate() <= alert.getNoResponseRateThreshold()) {
                return false;
            }

            List<String> recipients = recipients();
            if (recipients.isEmpty()) {
                log.warn("No Iris dashboard alert recipient configured. Set artemis.iris.dashboard.alert.recipients, digest.recipients, or info.contact.");
                return false;
            }

            Map<String, Object> context = new HashMap<>();
            context.put("overview", overview);
            context.put("from", from);
            context.put("to", now);
            context.put("threshold", alert.getNoResponseRateThreshold());
            context.put("dashboardUrl", DASHBOARD_URL);

            recipients.forEach(email -> mailSendingService.buildAndSendAsync(recipient(email), "email.irisDashboard.alert.title", "mail/irisDashboardAlert", context));
            stateMap.put(LAST_ALERT_SENT_AT_KEY, now);
            return true;
        }
        finally {
            stateMap.unlock(LAST_ALERT_SENT_AT_KEY);
        }
    }

    private IMap<String, Instant> getScheduleStateMap() {
        if (scheduleStateMap == null) {
            scheduleStateMap = hazelcastInstance.getMap(SCHEDULE_STATE_MAP);
        }
        return scheduleStateMap;
    }

    private List<String> recipients() {
        List<String> recipients = properties.getAlert().getRecipients().stream().filter(StringUtils::hasText).toList();
        if (!recipients.isEmpty()) {
            return recipients;
        }
        recipients = properties.getDigest().getRecipients().stream().filter(StringUtils::hasText).toList();
        if (!recipients.isEmpty()) {
            return recipients;
        }
        return StringUtils.hasText(adminEmail) ? List.of(adminEmail) : List.of();
    }

    private static User recipient(String email) {
        User user = new User();
        user.setEmail(email);
        user.setLogin("iris-dashboard-alert-recipient");
        user.setLangKey("en");
        user.setFirstName("Administrator");
        return user;
    }
}
