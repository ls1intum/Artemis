package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;

/**
 * Lightweight, cycle-free projection of {@link Conversation} embedded inside post response DTOs.
 * <p>
 * Mirrors the wire shape produced today by the polymorphic {@code @JsonTypeInfo}/{@code @JsonSubTypes}
 * configuration on {@code Conversation}: {@code type} is the discriminator string used by the
 * frontend ({@code "channel"}, {@code "groupChat"}, {@code "oneToOneChat"}).
 *
 * @param id       the conversation id
 * @param type     the discriminator string matching {@code @JsonSubTypes.Type.name} on {@link Conversation}
 * @param name     the displayed name of the conversation; {@code null} for one-to-one chats
 * @param courseId the id of the course the conversation belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationRefDTO(Long id, String type, @Nullable String name, @Nullable Long courseId) {

    /**
     * Build a {@link ConversationRefDTO} from a {@link Conversation} entity. Returns {@code null}
     * if the input is {@code null}.
     *
     * @param conversation the conversation to project, may be {@code null}
     * @return the projected reference, or {@code null} when input is {@code null}
     */
    public static @Nullable ConversationRefDTO from(@Nullable Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        Long courseId = conversation.getCourse() != null ? conversation.getCourse().getId() : null;
        return new ConversationRefDTO(conversation.getId(), discriminatorOf(conversation), nameOf(conversation), courseId);
    }

    private static String discriminatorOf(Conversation conversation) {
        return switch (conversation) {
            case Channel ignored -> "channel";
            case GroupChat ignored -> "groupChat";
            case OneToOneChat ignored -> "oneToOneChat";
            default -> conversation.getClass().getSimpleName();
        };
    }

    private static @Nullable String nameOf(Conversation conversation) {
        return switch (conversation) {
            case Channel channel -> channel.getName();
            case GroupChat groupChat -> groupChat.getName();
            default -> null;
        };
    }
}
