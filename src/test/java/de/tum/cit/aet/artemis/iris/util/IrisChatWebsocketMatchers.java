package de.tum.cit.aet.artemis.iris.util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.mockito.ArgumentMatcher;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContentResponseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;

public class IrisChatWebsocketMatchers {

    public static ArgumentMatcher<Object> statusDTO(PyrisRunState runState) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisChatWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisChatWebsocketDTO.IrisWebsocketMessageType.STATUS) {
                    return false;
                }
                return websocketDTO.runState() == runState;
            }

            @Override
            public String toString() {
                return "IrisChatWebsocketService.IrisWebsocketDTO with type STATUS and run state " + runState;
            }
        };
    }

    public static ArgumentMatcher<Object> messageDTO(String message) {
        return IrisChatWebsocketMatchers.messageDTO(List.of(new IrisTextMessageContent(message)));
    }

    public static ArgumentMatcher<Object> messageDTO(List<IrisMessageContent> content) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisChatWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisChatWebsocketDTO.IrisWebsocketMessageType.MESSAGE) {
                    return false;
                }
                List<String> actualContent = websocketDTO.message().content().stream().map(IrisMessageContentResponseDTO::textContent).toList();
                List<String> expectedContent = content.stream().map(IrisMessageContent::getContentAsString).toList();
                return Objects.equals(actualContent, expectedContent);
            }

            @Override
            public String toString() {
                return "IrisChatWebsocketService.IrisWebsocketDTO with type MESSAGE and content " + content;
            }
        };
    }

    public static ArgumentMatcher<Object> suggestionsDTO(String... suggestions) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisChatWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisChatWebsocketDTO.IrisWebsocketMessageType.STATUS) {
                    return false;
                }
                if (websocketDTO.suggestions() == null) {
                    return suggestions == null;
                }
                return websocketDTO.suggestions().equals(List.of(suggestions));
            }

            @Override
            public String toString() {
                return "IrisChatWebsocketService.IrisWebsocketDTO with type STATUS and suggestions " + Arrays.toString(suggestions);
            }
        };
    }

}
