package de.tum.cit.aet.artemis.notification.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Tells the client whether this installation can send mail, so that it can disable what depends on a mail being delivered. Changing
 * the e-mail address of an account is one of those things: it is confirmed from both addresses, so without mail it cannot be done.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class MailInfoContributor implements InfoContributor {

    private final MailSendingService mailSendingService;

    public MailInfoContributor(MailSendingService mailSendingService) {
        this.mailSendingService = mailSendingService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.MAIL_CONFIGURED, mailSendingService.isMailConfigured());
    }
}
