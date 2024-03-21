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

    /**
     * Generates a Reaction for the given User.
     *
     * @param user The User to generate the Reaction for
     * @return The generated Reaction
     */
    public static Reaction createReactionForUser(User user) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("heart");
        reaction.setUser(user);
        return reaction;
    }

    /**
     * Generates a Post for the given User. This method is used in a loop to generate multiple Posts.
     *
     * @param index  The index of the currently generated Post
     * @param author The User that is the author of the Post
     * @return The generated Post
     */
    public static Post createBasicPost(int index, User author) {
        Post post = new Post();
        post.setTitle(String.format("Title Post %s", (index + 1)));
        post.setContent(String.format("Content Post %s", (index + 1)));
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setAuthor(author);
        post.setCreationDate(ZonedDateTime.of(2015, 11, dayCount, 23, 45, 59, 1234, ZoneId.of("UTC")));
        String tag = String.format("Tag %s", (index + 1));
        Set<String> tags = new HashSet<>();
        tags.add(tag);
        post.setTags(tags);

        dayCount = (dayCount % 25) + 1;
        return post;
    }

    /**
     * Generates a Channel for the given Course.
     *
     * @param course The Course to generate the Channel for
     * @return The generated Channel
     */
    public static Channel generateCourseWideChannel(Course course) {
        return generatePublicChannel(course, "test", true);
    }

    /**
     * Generates a Channel for the given Course.
     *
     * @param course       The Course to generate the Channel for
     * @param channelName  The name of the Channel
     * @param isCourseWide True, if the Channel is course-wide
     * @return The generated Channel
     */
    public static Channel generatePublicChannel(Course course, String channelName, boolean isCourseWide) {
        return generatePublicChannel(course, channelName, isCourseWide, false);
    }

    /**
     * Generates a Channel for the given Course.
     *
     * @param course       The Course to generate the Channel for
     * @param channelName  The name of the Channel
     * @param isCourseWide True, if the Channel is course-wide
     * @return The generated Channel
     */
    public static Channel generateAnnouncementChannel(Course course, String channelName, boolean isCourseWide) {
        return generatePublicChannel(course, channelName, isCourseWide, true);
    }

    /**
     * Generates a Channel for the given Course.
     *
     * @param course         The Course to generate the Channel for
     * @param channelName    The name of the Channel
     * @param isCourseWide   True, if the Channel is course-wide
     * @param isAnnouncement True, if the Channel is an announcement channel
     * @return The generated Channel
     */
    public static Channel generatePublicChannel(Course course, String channelName, boolean isCourseWide, boolean isAnnouncement) {
        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName(channelName);
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(isAnnouncement);
        channel.setIsArchived(false);
        channel.setIsCourseWide(isCourseWide);
        channel.setDescription("Test channel");
        return channel;
    }
}
