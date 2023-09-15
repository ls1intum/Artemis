package de.tum.in.www1.artemis.post;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

/**
 * Factory for creating objects related to Conversations.
 */
public class ConversationFactory {

    private static int dayCount = 1;

    public static Reaction createReactionForUser(User user) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("heart");
        reaction.setUser(user);
        return reaction;
    }

    public static Post createBasicPost(Integer i, User author) {
        Post post = new Post();
        post.setTitle(String.format("Title Post %s", (i + 1)));
        post.setContent(String.format("Content Post %s", (i + 1)));
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setAuthor(author);
        post.setCreationDate(ZonedDateTime.of(2015, 11, dayCount, 23, 45, 59, 1234, ZoneId.of("UTC")));
        String tag = String.format("Tag %s", (i + 1));
        Set<String> tags = new HashSet<>();
        tags.add(tag);
        post.setTags(tags);

        dayCount = (dayCount % 25) + 1;
        return post;
    }

    public static Channel generateCourseWideChannel(Course course) {
        return generatePublicChannel(course, "test", true);
    }

    public static Channel generatePublicChannel(Course course, String channelName, boolean isCourseWide) {
        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName(channelName);
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setIsCourseWide(isCourseWide);
        channel.setDescription("Test channel");
        return channel;
    }
}
