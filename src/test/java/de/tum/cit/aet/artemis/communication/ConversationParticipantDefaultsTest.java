package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;

class ConversationParticipantDefaultsTest {

    @Test
    void newParticipantsAlwaysHaveBooleanPreferences() {
        var participant = new ConversationParticipant();
        assertThat(participant.getIsFavorite()).isFalse();
        assertThat(participant.getIsHidden()).isFalse();
        assertThat(participant.getIsMuted()).isFalse();
    }
}
