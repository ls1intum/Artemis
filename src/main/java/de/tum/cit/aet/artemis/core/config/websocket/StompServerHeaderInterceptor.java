package de.tum.cit.aet.artemis.core.config.websocket;

import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Removes the {@code server} header from the STOMP CONNECTED frame before it reaches a client.
 * <p>
 * With the broker relay (multi-node setup), Spring forwards the CONNECTED frame of the external broker unchanged. The broker names its product and version in the
 * {@code server} header, e.g. {@code ActiveMQ-Artemis/2.41.0 ActiveMQ Artemis Messaging Engine}. The client does not use this header, and the broker is an implementation
 * detail of the server, so it does not belong in the frame the client receives.
 */
public class StompServerHeaderInterceptor implements ChannelInterceptor {

    static final String SERVER_HEADER = "server";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECTED || accessor.getFirstNativeHeader(SERVER_HEADER) == null) {
            return message;
        }
        // Work on a copy: the accessor of the received frame may already be immutable, and the native headers of the copy are mutable.
        StompHeaderAccessor filteredAccessor = StompHeaderAccessor.wrap(message);
        filteredAccessor.removeNativeHeader(SERVER_HEADER);
        return MessageBuilder.createMessage(message.getPayload(), filteredAccessor.getMessageHeaders());
    }
}
