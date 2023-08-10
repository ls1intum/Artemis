package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;

public class ConversationParticipantSettingsView {

    private final Long id;

    private final Boolean isModerator;

    private final Boolean isFavorite;

    private final Boolean isHidden;

    private final ZonedDateTime lastRead;

    public ConversationParticipantSettingsView(Long id, Boolean isModerator, Boolean isFavorite, Boolean isHidden, ZonedDateTime lastRead) {
        this.id = id;
        this.isModerator = isModerator;
        this.isFavorite = isFavorite;
        this.isHidden = isHidden;
        this.lastRead = lastRead;
    }

    public Long getId() {
        return id;
    }

    public Boolean getIsModerator() {
        return isModerator;
    }

    public Boolean getIsFavorite() {
        return isFavorite;
    }

    public Boolean getIsHidden() {
        return isHidden;
    }

    public ZonedDateTime getLastRead() {
        return lastRead;
    }
}
