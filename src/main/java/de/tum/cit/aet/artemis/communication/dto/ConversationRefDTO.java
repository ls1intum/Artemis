package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;

/**
 * Cycle-free projection of {@link Conversation} embedded inside post response DTOs.
 * <p>
 * Mirrors the wire shape produced today by the polymorphic {@code @JsonTypeInfo}/{@code @JsonSubTypes}
 * configuration on {@code Conversation}: {@code type} is the discriminator string used by the
 * frontend ({@code "channel"}, {@code "groupChat"}, {@code "oneToOneChat"}). The channel-only
 * fields ({@code isCourseWide}, {@code isAnnouncementChannel}, {@code isArchived}, {@code isPublic})
 * are carried because the frontend reads them off {@code post.conversation} directly for the
 * "course-wide filter" path, announcement-channel reaction gating, archived-banner rendering, and
 * channel-moderation permission checks. {@code lastMessageDate} feeds the unread-marker logic in
 * the course-wide search view. None of these fields can introduce a JSON cycle.
 *
 * @param id                    the conversation id
 * @param type                  the discriminator string matching {@code @JsonSubTypes.Type.name} on {@link Conversation}
 * @param name                  the displayed name of the conversation; {@code null} for one-to-one chats
 * @param isPublic              whether the channel is public; {@code null} for non-channels
 * @param isAnnouncementChannel whether the channel is an announcement channel; {@code null} for non-channels
 * @param isArchived            whether the channel is archived; {@code null} for non-channels
 * @param isCourseWide          whether the channel is course-wide; {@code null} for non-channels
 * @param lastMessageDate       when the last message was posted in this conversation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationRefDTO(Long id, String type, @Nullable String name, @Nullable Boolean isPublic, @Nullable Boolean isAnnouncementChannel, @Nullable Boolean isArchived,
        @Nullable Boolean isCourseWide, @Nullable ZonedDateTime lastMessageDate) {

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
        Boolean isPublic = null;
        Boolean isAnnouncement = null;
        Boolean isArchived = null;
        Boolean isCourseWide = null;
        if (conversation instanceof Channel channel) {
            isPublic = channel.getIsPublic();
            isAnnouncement = channel.getIsAnnouncementChannel();
            isArchived = channel.getIsArchived();
            isCourseWide = channel.getIsCourseWide();
        }
        return new ConversationRefDTO(conversation.getId(), discriminatorOf(conversation), nameOf(conversation), isPublic, isAnnouncement, isArchived, isCourseWide,
                conversation.getLastMessageDate());
    }

    private static String discriminatorOf(Conversation conversation) {
        return switch (conversation) {
            case Channel ignored -> "channel";
            case GroupChat ignored -> "groupChat";
            case OneToOneChat ignored -> "oneToOneChat";
            default -> throw new IllegalStateException("Unknown Conversation subtype: " + conversation.getClass().getName()
                    + " — add the new subtype to ConversationRefDTO.discriminatorOf and to @JsonSubTypes on Conversation.");
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
