package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class WebsocketInboundOrderTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private WebSocketHandler subProtocolWebSocketHandler;

    @Test
    void stompFramesOfOneSessionAreHandledInReceiveOrder() {
        assertThat(subProtocolWebSocketHandler).isInstanceOf(SubProtocolWebSocketHandler.class);
        var protocolHandlers = ((SubProtocolWebSocketHandler) subProtocolWebSocketHandler).getProtocolHandlers();
        assertThat(protocolHandlers).isNotEmpty().allSatisfy(handler -> {
            assertThat(handler).isInstanceOf(StompSubProtocolHandler.class);
            assertThat(((StompSubProtocolHandler) handler).isPreserveReceiveOrder()).isTrue();
        });
    }
}
