package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestDTO;
import de.tum.cit.aet.artemis.iris.service.IrisDashboardEmailService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

class IrisDashboardEmailServiceTest {

    private MailService mailService;

    private MailSendingService mailSendingService;

    private IrisDashboardProperties properties;

    private IrisDashboardEmailService emailService;

    @BeforeEach
    void setUp() {
        mailService = mock(MailService.class);
        mailSendingService = mock(MailSendingService.class);
        properties = new IrisDashboardProperties();
        when(mailSendingService.isMailConfigured()).thenReturn(true);
    }

    private void initService() {
        emailService = new IrisDashboardEmailService(mailService, mailSendingService, properties);
        emailService.init();
    }

    @Test
    void canSendDigest_noRecipients_returnsFalse() {
        properties.getDigest().setRecipients(List.of());
        initService();
        assertThat(emailService.canSendDigest()).isFalse();
    }

    @Test
    void canSendDigest_validRecipients_returnsTrue() {
        properties.getDigest().setRecipients(List.of("admin@example.com"));
        initService();
        assertThat(emailService.canSendDigest()).isTrue();
    }

    @Test
    void canSendDigest_mailNotConfigured_returnsFalse() {
        when(mailSendingService.isMailConfigured()).thenReturn(false);
        properties.getDigest().setRecipients(List.of("admin@example.com"));
        initService();
        assertThat(emailService.canSendDigest()).isFalse();
    }

    @Test
    void sendDigest_validRecipients_sendsToAll() {
        properties.getDigest().setRecipients(List.of("a@test.com", "b@test.com"));
        initService();

        IrisDashboardDigestDTO digest = mock(IrisDashboardDigestDTO.class);
        int sent = emailService.sendDigest(digest);

        assertThat(sent).isEqualTo(2);
        verify(mailService, times(2)).sendIrisDashboardDigestEmail(any(MailRecipientDTO.class), any());
    }

    @Test
    void sendDigest_duplicateRecipients_deduped() {
        properties.getDigest().setRecipients(List.of("a@test.com", "A@TEST.COM"));
        initService();

        IrisDashboardDigestDTO digest = mock(IrisDashboardDigestDTO.class);
        int sent = emailService.sendDigest(digest);

        assertThat(sent).isEqualTo(1);
    }

    @Test
    void sendDigest_invalidEmail_excluded() {
        properties.getDigest().setRecipients(List.of("Admin <a@test.com>", "valid@test.com"));
        initService();

        IrisDashboardDigestDTO digest = mock(IrisDashboardDigestDTO.class);
        int sent = emailService.sendDigest(digest);

        assertThat(sent).isEqualTo(1);
    }

    @Test
    void canSendAlert_fallsBackToDigestRecipients() {
        properties.getAlert().setRecipients(List.of());
        properties.getDigest().setRecipients(List.of("admin@example.com"));
        initService();
        assertThat(emailService.canSendAlert()).isTrue();
    }

    @Test
    void invalidAndEmptyRecipientsAreDroppedBeforeCaseInsensitiveDeduplication() {
        properties.getDigest().setRecipients(java.util.Arrays.asList(null, " ", "not an address", " Admin <admin@example.org> ", " admin@example.org ", "ADMIN@EXAMPLE.ORG"));
        initService();
        var digest = mock(IrisDashboardDigestDTO.class);
        assertThat(emailService.sendDigest(digest)).isEqualTo(1);
        var recipient = org.mockito.ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailService).sendIrisDashboardDigestEmail(recipient.capture(), org.mockito.ArgumentMatchers.eq(digest));
        assertThat(recipient.getValue().email()).isEqualTo("admin@example.org");
    }

    @Test
    void alertRecipientsOverrideDigestFallbackAndFailureDoesNotStopRemainingDelivery() {
        properties.getDigest().setRecipients(List.of("digest@example.org"));
        properties.getAlert().setRecipients(List.of("first@example.org", "second@example.org"));
        initService();
        var alert = mock(de.tum.cit.aet.artemis.iris.dto.IrisDashboardAlertDTO.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("queue failed")).doNothing().when(mailService).sendIrisDashboardAlertEmail(any(), any());
        assertThat(emailService.sendAlert(alert)).isEqualTo(1);
        var recipient = org.mockito.ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailService, times(2)).sendIrisDashboardAlertEmail(recipient.capture(), org.mockito.ArgumentMatchers.eq(alert));
        assertThat(recipient.getAllValues()).extracting(MailRecipientDTO::email).containsExactly("first@example.org", "second@example.org");
    }

    @Test
    void digestFailureDoesNotStopRemainingDelivery() {
        properties.getDigest().setRecipients(List.of("first@example.org", "second@example.org"));
        initService();
        org.mockito.Mockito.doThrow(new IllegalStateException("queue failed")).doNothing().when(mailService).sendIrisDashboardDigestEmail(any(), any());
        assertThat(emailService.sendDigest(mock(IrisDashboardDigestDTO.class))).isEqualTo(1);
        verify(mailService, times(2)).sendIrisDashboardDigestEmail(any(), any());
    }

    @Test
    void repeatedCapabilityChecksRemainFalseWhenMailOrRecipientsAreUnavailable() {
        properties.getDigest().setRecipients(null);
        properties.getAlert().setRecipients(null);
        initService();
        assertThat(emailService.canSendDigest()).isFalse();
        assertThat(emailService.canSendDigest()).isFalse();
        assertThat(emailService.canSendAlert()).isFalse();
        assertThat(emailService.canSendAlert()).isFalse();
        when(mailSendingService.isMailConfigured()).thenReturn(false);
        assertThat(emailService.canSendAlert()).isFalse();
        assertThat(emailService.canSendAlert()).isFalse();
    }
}
