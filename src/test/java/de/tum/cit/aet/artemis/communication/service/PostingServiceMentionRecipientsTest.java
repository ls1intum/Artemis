package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;

/**
 * Tests the helpers of {@link PostingService} that decide who receives the mention notification of a posting that pings "@all".
 * The helpers are protected and static, so they are called from the package of the service.
 */
class PostingServiceMentionRecipientsTest {

    private static final long AUTHOR_ID = 1L;

    private static final long MUTED_ID = 2L;

    private static final long HIDDEN_ID = 3L;

    private static final long MEMBER_ID = 4L;

    private static final long OTHER_MEMBER_ID = 5L;

    private static ConversationNotificationRecipientSummary summary(long userId, boolean muted, boolean hidden) {
        return new ConversationNotificationRecipientSummary(userId, "login" + userId, "First" + userId, "Last" + userId, userId % 2 == 0 ? "de" : "en",
                "User" + userId + "@Example.org", muted, hidden, false);
    }

    private static List<ConversationNotificationRecipientSummary> conversationMembers() {
        return List.of(summary(AUTHOR_ID, false, false), summary(MUTED_ID, true, false), summary(HIDDEN_ID, false, true), summary(MEMBER_ID, false, false),
                summary(OTHER_MEMBER_ID, false, false));
    }

    private static User explicitlyMentioned(long userId) {
        User user = new User(userId);
        user.setLogin("explicit" + userId);
        return user;
    }

    private static List<Long> ids(List<User> users) {
        return users.stream().map(User::getId).toList();
    }

    private static Stream<Arguments> conversationsAndContent() {
        return Stream.of(Arguments.of("group chat with the token", new GroupChat(), "@all please read", true),
                Arguments.of("group chat with the token in a different case", new GroupChat(), "@ALL", true),
                Arguments.of("group chat with the token behind a user mention", new GroupChat(), "[user]A B(ab)[/user] @all", true),
                Arguments.of("group chat without the token", new GroupChat(), "please read", false), Arguments.of("group chat without content", new GroupChat(), null, false),
                Arguments.of("group chat with the token in a quote", new GroupChat(), "> @all\n\nplease read", false),
                Arguments.of("group chat with the token in code", new GroupChat(), "`@all`", false),
                Arguments.of("channel with the token", new Channel(), "@all please read", false),
                Arguments.of("one-to-one chat with the token", new OneToOneChat(), "@all please read", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conversationsAndContent")
    void testMentionsAllMembers(String description, Conversation conversation, String content, boolean expected) {
        assertThat(PostingService.mentionsAllMembers(conversation, content)).isEqualTo(expected);
    }

    @Test
    void testResolveMentionRecipientsReturnsTheExplicitRecipientsIfThePostingDoesNotPingAllMembers() {
        List<User> explicit = List.of(explicitlyMentioned(MEMBER_ID));

        var recipients = PostingService.resolveMentionRecipients(explicit, false, conversationMembers(), AUTHOR_ID);

        // the members of the conversation are not looked at at all
        assertThat(recipients).isSameAs(explicit);
        assertThat(ids(recipients)).containsExactly(MEMBER_ID);
    }

    @Test
    void testResolveMentionRecipientsAddsEveryMemberWhoDidNotMuteOrHideTheConversationAndSkipsTheAuthor() {
        var recipients = PostingService.resolveMentionRecipients(List.of(), true, conversationMembers(), AUTHOR_ID);

        // the author, the member who muted the conversation and the member who hid it are left out
        assertThat(ids(recipients)).containsExactly(MEMBER_ID, OTHER_MEMBER_ID);
    }

    @Test
    void testResolveMentionRecipientsKeepsExplicitlyMentionedUsersEvenIfTheyMutedOrHidTheConversation() {
        List<User> explicit = List.of(explicitlyMentioned(MUTED_ID), explicitlyMentioned(HIDDEN_ID));

        var recipients = PostingService.resolveMentionRecipients(explicit, true, conversationMembers(), AUTHOR_ID);

        // a mention by name reaches the member although they muted the conversation, the "@all" only adds the members who want to be notified
        assertThat(ids(recipients)).containsExactly(MUTED_ID, HIDDEN_ID, MEMBER_ID, OTHER_MEMBER_ID);
        assertThat(recipients.get(0)).isSameAs(explicit.get(0));
        assertThat(recipients.get(1)).isSameAs(explicit.get(1));
    }

    @Test
    void testResolveMentionRecipientsContainsAUserOnceIfTheyAreMentionedByNameAndThroughAtAll() {
        User explicitMember = explicitlyMentioned(MEMBER_ID);

        var recipients = PostingService.resolveMentionRecipients(List.of(explicitMember), true, conversationMembers(), AUTHOR_ID);

        assertThat(ids(recipients)).containsExactly(MEMBER_ID, OTHER_MEMBER_ID);
        // the explicitly mentioned user is kept, the entry of the conversation member does not replace it
        assertThat(recipients.getFirst()).isSameAs(explicitMember);
        assertThat(recipients.getFirst().getLogin()).isEqualTo("explicit" + MEMBER_ID);
    }

    @Test
    void testResolveMentionRecipientsCopiesTheDeliveryDataOfTheMembers() {
        var recipients = PostingService.resolveMentionRecipients(List.of(), true, List.of(summary(MEMBER_ID, false, false), summary(OTHER_MEMBER_ID, false, false)), AUTHOR_ID);

        // the notification channels need the login, the name, the language and the email of the recipient, none of them is loaded again
        assertThat(recipients).hasSize(2);
        User member = recipients.getFirst();
        assertThat(member.getId()).isEqualTo(MEMBER_ID);
        assertThat(member.getLogin()).isEqualTo("login" + MEMBER_ID);
        assertThat(member.getFirstName()).isEqualTo("First" + MEMBER_ID);
        assertThat(member.getLastName()).isEqualTo("Last" + MEMBER_ID);
        assertThat(member.getLangKey()).isEqualTo("de");
        assertThat(member.getEmail()).isEqualTo("user4@example.org");
        User otherMember = recipients.getLast();
        assertThat(otherMember.getId()).isEqualTo(OTHER_MEMBER_ID);
        assertThat(otherMember.getLangKey()).isEqualTo("en");
        assertThat(otherMember.getEmail()).isEqualTo("user5@example.org");
    }

    @Test
    void testResolveMentionRecipientsToleratesMembersWithoutNameOrEmail() {
        var memberWithoutDetails = new ConversationNotificationRecipientSummary(MEMBER_ID, "login4", null, null, null, null, false, false, false);

        var recipients = PostingService.resolveMentionRecipients(List.of(), true, List.of(memberWithoutDetails), AUTHOR_ID);

        assertThat(recipients).hasSize(1);
        assertThat(recipients.getFirst().getId()).isEqualTo(MEMBER_ID);
        assertThat(recipients.getFirst().getLogin()).isEqualTo("login4");
        assertThat(recipients.getFirst().getEmail()).isNull();
        assertThat(recipients.getFirst().getLangKey()).isNull();
    }

    @Test
    void testResolveMentionRecipientsIsEmptyIfNobodyWantsToBeNotified() {
        var onlyTheAuthorAndMutedMembers = List.of(summary(AUTHOR_ID, false, false), summary(MUTED_ID, true, false), summary(HIDDEN_ID, false, true));

        assertThat(PostingService.resolveMentionRecipients(List.of(), true, onlyTheAuthorAndMutedMembers, AUTHOR_ID)).isEmpty();
        assertThat(PostingService.resolveMentionRecipients(List.of(), true, List.of(), AUTHOR_ID)).isEmpty();
    }

    @Test
    void testResolveMentionRecipientsSkipsAMemberWhoBothMutedAndHidTheConversation() {
        var mutedAndHidden = summary(MEMBER_ID, true, true);

        assertThat(PostingService.resolveMentionRecipients(List.of(), true, List.of(mutedAndHidden, summary(OTHER_MEMBER_ID, false, false)), AUTHOR_ID)).extracting(User::getId)
                .containsExactly(OTHER_MEMBER_ID);
    }

    @Test
    void testResolveMentionRecipientsDoesNotChangeItsArguments() {
        List<User> explicit = List.of(explicitlyMentioned(MEMBER_ID));
        var members = conversationMembers();

        var recipients = PostingService.resolveMentionRecipients(explicit, true, members, AUTHOR_ID);

        // the immutable arguments would throw if the method tried to change them, the result is a new list
        assertThat(recipients).isNotSameAs(explicit);
        assertThat(ids(explicit)).containsExactly(MEMBER_ID);
        assertThat(members).hasSize(5);
    }
}
