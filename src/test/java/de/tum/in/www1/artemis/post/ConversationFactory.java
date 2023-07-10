package de.tum.in.www1.artemis.post;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

/**
 * Factory for creating conversations and related objects.
 */
public class ConversationFactory {

    public static Channel generateChannel(Course course) {
        return generateChannel(course, "test");
    }

    public static Channel generateChannel(Course course, String channelName) {
        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName(channelName);
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setDescription("Test channel");
        return channel;
    }
}
