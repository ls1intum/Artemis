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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardDigestDTO;
import de.tum.cit.aet.artemis.iris.service.IrisDashboardEmailService;
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
        verify(mailService, times(2)).sendIrisDashboardDigestEmail(any(User.class), any());
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
}
