package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.weekly_summary_mail;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

public record WeeklySummaryMailRecipientDTO(String langKey, String email, String name) implements IMailRecipientUserDTO {

    public static WeeklySummaryMailRecipientDTO of(User user) {
        return new WeeklySummaryMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getName());
    }
}
