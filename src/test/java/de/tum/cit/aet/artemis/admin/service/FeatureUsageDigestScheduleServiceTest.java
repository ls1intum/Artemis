package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

/**
 * Tests the guards around the weekly email.
 * <p>
 * Every one of them exists to stop mail going out when it should not: duplicated across a cluster, sent from a developer
 * machine or a test server, or sent at all when the deployment switched the digest off. Getting a guard wrong here is
 * visible to a human inbox, which is why they are pinned individually.
 */
class FeatureUsageDigestScheduleServiceTest {

    private static final String ADMIN_EMAIL = "admin@example.com";

    private FeatureUsageDigestService digestService;

    private MailService mailService;

    private ProfileService profileService;

    @BeforeEach
    void init() {
        digestService = mock(FeatureUsageDigestService.class);
        mailService = mock(MailService.class);
        profileService = mock(ProfileService.class);
        when(profileService.isSchedulingActive()).thenReturn(true);
        when(profileService.isDevActive()).thenReturn(false);
        when(digestService.buildWeeklyDigest()).thenReturn(digest());
        // The digest is now sent synchronously and reports whether it reached the transport, so a boolean-returning mock
        // would otherwise answer false and make every test here see a failed send.
        lenient().when(mailService.sendFeatureUsageDigestEmail(any(), any())).thenReturn(true);
    }

    /**
     * The manual trigger exists so an administrator can find out whether the weekly mail will actually arrive. It used to
     * queue an asynchronous send and answer true regardless, so the page said the mail had been sent while an
     * unconfigured mail server meant nothing had left at all.
     */
    @Test
    void shouldNotReportSuccessWhenAMailDidNotReachTheTransport() {
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, false);
        when(mailService.sendFeatureUsageDigestEmail(any(), any())).thenReturn(false);

        assertThat(service.sendDigestEmail()).isFalse();
    }

    @Test
    void shouldReportSuccessOnlyWhenEveryRecipientWasReached() {
        var service = service(enabledDigest(List.of("first@example.com", "second@example.com")), ADMIN_EMAIL, false);
        when(mailService.sendFeatureUsageDigestEmail(any(), any())).thenReturn(true, false);

        // one of the two did not go out, so the administrator must not be told the digest was sent
        assertThat(service.sendDigestEmail()).isFalse();
        verify(mailService, times(2)).sendFeatureUsageDigestEmail(any(), any());
    }

    @Test
    void shouldSendToTheAdminContactByDefault() {
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, false);

        service.sendWeeklyDigest();

        ArgumentCaptor<MailRecipientDTO> recipient = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailService).sendFeatureUsageDigestEmail(recipient.capture(), any());
        assertThat(recipient.getValue().email()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void shouldSendToEveryConfiguredRecipient() {
        var service = service(enabledDigest(List.of("first@example.com", "second@example.com")), ADMIN_EMAIL, false);

        service.sendWeeklyDigest();

        verify(mailService, times(2)).sendFeatureUsageDigestEmail(any(), any());
    }

    @Test
    void shouldPreferConfiguredRecipientsOverTheAdminContact() {
        var service = service(enabledDigest(List.of("digest@example.com")), ADMIN_EMAIL, false);

        service.sendWeeklyDigest();

        ArgumentCaptor<MailRecipientDTO> recipient = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailService).sendFeatureUsageDigestEmail(recipient.capture(), any());
        assertThat(recipient.getValue().email()).isEqualTo("digest@example.com");
    }

    @Test
    void shouldIgnoreBlankConfiguredRecipients() {
        var service = service(enabledDigest(List.of(" ", "")), ADMIN_EMAIL, false);

        service.sendWeeklyDigest();

        ArgumentCaptor<MailRecipientDTO> recipient = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailService).sendFeatureUsageDigestEmail(recipient.capture(), any());
        assertThat(recipient.getValue().email()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void shouldNotSendWithoutAnyRecipient() {
        var service = service(enabledDigest(List.of()), "", false);

        assertThat(service.sendDigestEmail()).isFalse();
        verifyNoInteractions(mailService);
    }

    @Test
    void shouldNotSendOnANodeWithoutTheSchedulingProfile() {
        when(profileService.isSchedulingActive()).thenReturn(false);
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, false);

        // otherwise every node of the cluster would send its own copy of the same email
        service.sendWeeklyDigest();

        verifyNoInteractions(mailService);
    }

    @Test
    void shouldNotSendFromADevelopmentEnvironment() {
        when(profileService.isDevActive()).thenReturn(true);
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, false);

        service.sendWeeklyDigest();

        verifyNoInteractions(mailService);
    }

    @Test
    void shouldNotSendFromATestServer() {
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, true);

        service.sendWeeklyDigest();

        verifyNoInteractions(mailService);
    }

    @Test
    void shouldNotSendWhenTheDigestIsDisabled() {
        var service = service(new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of())), ADMIN_EMAIL, false);

        assertThat(service.sendDigestEmail()).isFalse();
        verifyNoInteractions(mailService);
        verify(digestService, never()).buildWeeklyDigest();
    }

    @Test
    void shouldNotSendWhenTrackingItselfIsDisabled() {
        var service = service(new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(true, List.of())), ADMIN_EMAIL, false);

        // there would be nothing to report, and an email full of zeros reads as a broken deployment
        assertThat(service.sendDigestEmail()).isFalse();
        verifyNoInteractions(mailService);
    }

    @Test
    void shouldReportFailureWithoutPropagatingIt() {
        when(digestService.buildWeeklyDigest()).thenThrow(new IllegalStateException("database down"));
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, false);

        // a scheduled job must not escalate; the manual trigger turns the false into a 400 instead
        assertThat(service.sendDigestEmail()).isFalse();
    }

    @Test
    void shouldAllowAManualSendEvenWithoutTheSchedulingProfile() {
        when(profileService.isSchedulingActive()).thenReturn(false);
        var service = service(enabledDigest(List.of()), ADMIN_EMAIL, true);

        // the point of the manual trigger is to check delivery from any node, including a test server
        assertThat(service.sendDigestEmail()).isTrue();
        verify(mailService).sendFeatureUsageDigestEmail(any(), any());
    }

    private FeatureUsageDigestScheduleService service(FeatureUsageProperties properties, String adminEmail, boolean isTestServer) {
        // Run inline, matching the SyncTaskExecutor the mail executor resolves to under the test profile.
        var service = new FeatureUsageDigestScheduleService(digestService, properties, mailService, profileService, Runnable::run);
        ReflectionTestUtils.setField(service, "adminEmail", adminEmail);
        ReflectionTestUtils.setField(service, "isTestServer", isTestServer);
        return service;
    }

    private static FeatureUsageProperties enabledDigest(List<String> recipients) {
        return new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(true, recipients));
    }

    private static FeatureUsageDigestDTO digest() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return new FeatureUsageDigestDTO(7, today.minusDays(6), today, 10, 5, 3, 2, 1, 0, Instant.now(), List.of(), List.of());
    }
}
