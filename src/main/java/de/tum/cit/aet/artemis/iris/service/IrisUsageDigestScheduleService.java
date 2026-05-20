package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Lazy
@Service
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
public class IrisUsageDigestScheduleService {

    private static final Logger log = LoggerFactory.getLogger(IrisUsageDigestScheduleService.class);

    private static final String DASHBOARD_URL = "/admin/iris-dashboard";

    private static final String SCHEDULE_STATE_MAP = "iris-dashboard-schedule-state";

    private static final String DIGEST_SENT_KEY_PREFIX = "digest-sent:";

    private static final int DIGEST_SENT_STATE_TTL_DAYS = 3;

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

    public IrisUsageDigestScheduleService(IrisAdminDashboardService dashboardService, IrisDashboardProperties properties, ProfileService profileService,
            MailSendingService mailSendingService, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.dashboardService = dashboardService;
        this.properties = properties;
        this.profileService = profileService;
        this.mailSendingService = mailSendingService;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Sends the scheduled daily Iris usage digest when dashboard digests are enabled.
     */
    @Scheduled(cron = "${artemis.iris.dashboard.digest.cron:0 0 7 * * *}")
    public void sendDailyDigest() {
        if (!profileService.isSchedulingActive() || profileService.isDevActive() || isTestServer || !properties.getDigest().isEnabled()) {
            return;
        }
        LocalDate yesterday = TimeUtil.now().withZoneSameInstant(ZoneOffset.UTC).toLocalDate().minusDays(1);
        Instant from = yesterday.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = yesterday.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        sendScheduledDigestForWindow(from, to);
    }

    /**
     * Sends a digest for an explicit window.
     *
     * @param from the inclusive UTC start
     * @param to the exclusive UTC end
     * @return true if at least one digest email was scheduled
     */
    public boolean sendDigestForWindow(Instant from, Instant to) {
        List<String> recipients = recipients(properties.getDigest().getRecipients(), properties.getAlert().getRecipients());
        if (recipients.isEmpty()) {
            log.warn("No Iris dashboard digest recipient configured. Set artemis.iris.dashboard.digest.recipients, artemis.iris.dashboard.alert.recipients, or info.contact.");
            return false;
        }

        IrisDashboardOverviewDTO overview = dashboardService.getOverview(from, to, null);
        Map<String, Object> context = new HashMap<>();
        context.put("overview", overview);
        context.put("from", from);
        context.put("to", to);
        context.put("dashboardUrl", DASHBOARD_URL);

        recipients.forEach(email -> mailSendingService.buildAndSendAsync(recipient(email, "iris-dashboard-digest-recipient"), "email.irisDashboard.digest.title",
                "mail/irisDashboardDigest", context));
        return true;
    }

    private void sendScheduledDigestForWindow(Instant from, Instant to) {
        String digestSentKey = DIGEST_SENT_KEY_PREFIX + from + ":" + to;
        IMap<String, Instant> stateMap = getScheduleStateMap();
        stateMap.lock(digestSentKey);
        try {
            if (stateMap.containsKey(digestSentKey)) {
                return;
            }
            if (sendDigestForWindow(from, to)) {
                stateMap.put(digestSentKey, TimeUtil.now().toInstant(), DIGEST_SENT_STATE_TTL_DAYS, TimeUnit.DAYS);
            }
        }
        finally {
            stateMap.unlock(digestSentKey);
        }
    }

    private IMap<String, Instant> getScheduleStateMap() {
        if (scheduleStateMap == null) {
            scheduleStateMap = hazelcastInstance.getMap(SCHEDULE_STATE_MAP);
        }
        return scheduleStateMap;
    }

    private List<String> recipients(List<String> configuredRecipients, List<String> fallbackRecipients) {
        List<String> recipients = configuredRecipients.stream().filter(StringUtils::hasText).toList();
        if (!recipients.isEmpty()) {
            return recipients;
        }
        recipients = fallbackRecipients.stream().filter(StringUtils::hasText).toList();
        if (!recipients.isEmpty()) {
            return recipients;
        }
        return StringUtils.hasText(adminEmail) ? List.of(adminEmail) : List.of();
    }

    private static User recipient(String email, String login) {
        User user = new User();
        user.setEmail(email);
        user.setLogin(login);
        user.setLangKey("en");
        user.setFirstName("Administrator");
        return user;
    }
}
