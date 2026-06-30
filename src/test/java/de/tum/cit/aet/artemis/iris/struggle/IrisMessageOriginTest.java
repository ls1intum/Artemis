package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;

class IrisMessageOriginTest {

    @Test
    void originIsNullByDefaultAndSettable() {
        var message = new IrisMessage();
        assertThat(message.getOrigin()).isNull();
        message.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        assertThat(message.getOrigin()).isEqualTo(IrisMessageOrigin.PROACTIVE_STRUGGLE);
    }

    @Test
    void responseDtoExposesOriginToClients() {
        var message = new IrisMessage();
        message.setSender(IrisMessageSender.LLM);
        message.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        message.addContent(new IrisTextMessageContent("nudge"));
        var dto = IrisMessageResponseDTO.of(message);
        assertThat(dto.origin()).isEqualTo(IrisMessageOrigin.PROACTIVE_STRUGGLE);
    }

    @Test
    void responseDtoExposesProactiveEpisodeId() {
        // Step 1 DTO contract: IrisMessageResponseDTO.of(msg) carries proactiveEpisodeId when set.
        var message = new IrisMessage();
        message.setSender(IrisMessageSender.LLM);
        message.setProactiveEpisodeId("ep-999");
        var dto = IrisMessageResponseDTO.of(message);
        assertThat(dto.proactiveEpisodeId()).isEqualTo("ep-999");
    }

    @Test
    void responseDtoProactiveEpisodeIdIsNullWhenNotSet() {
        var message = new IrisMessage();
        message.setSender(IrisMessageSender.LLM);
        var dto = IrisMessageResponseDTO.of(message);
        assertThat(dto.proactiveEpisodeId()).isNull();
    }
}
