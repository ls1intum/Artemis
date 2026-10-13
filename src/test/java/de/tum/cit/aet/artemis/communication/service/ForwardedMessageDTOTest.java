package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.dto.ForwardedMessageDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

class ForwardedMessageDTOTest {

    @Test
    void toEntityWithDestinationPostSetsOnlyThePost() {
        ForwardedMessage entity = new ForwardedMessageDTO(1L, 2L, PostingType.POST, 3L, null).toEntity();

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getSourceId()).isEqualTo(2L);
        assertThat(entity.getSourceType()).isEqualTo(PostingType.POST);
        assertThat(entity.getDestinationPost().getId()).isEqualTo(3L);
        assertThat(entity.getDestinationAnswerPost()).isNull();
    }

    @Test
    void toEntityWithDestinationAnswerPostSetsOnlyTheAnswerPost() {
        ForwardedMessage entity = new ForwardedMessageDTO(1L, 2L, PostingType.ANSWER, null, 4L).toEntity();

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getSourceType()).isEqualTo(PostingType.ANSWER);
        assertThat(entity.getDestinationPost()).isNull();
        assertThat(entity.getDestinationAnswerPost().getId()).isEqualTo(4L);
    }

    @Test
    void toEntityRejectsZeroOrTwoDestinations() {
        assertThatThrownBy(() -> new ForwardedMessageDTO(1L, 2L, PostingType.POST, null, null).toEntity()).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> new ForwardedMessageDTO(1L, 2L, PostingType.POST, 3L, 4L).toEntity()).isInstanceOf(BadRequestAlertException.class);
    }
}
