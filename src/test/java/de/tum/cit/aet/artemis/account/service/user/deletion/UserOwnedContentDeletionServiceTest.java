package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.cleanup.CommunicationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.UserReferenceCount;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Covers the deletion of the content an account owns rather than merely points at.
 *
 * <p>
 * These statements reach further than a single foreign key - down a discussion thread, across a join table - and
 * several of them have to be written natively, where nothing checks them before they run. The first test builds the
 * shape that is hardest to get right; the second insists that every one of them at least executes.
 */
class UserOwnedContentDeletionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ownedcontent";

    @Autowired
    private UserOwnedContentDeletionService userOwnedContentDeletionService;

    @Autowired
    private CommunicationDataCleanupRepository communicationDataCleanupRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Test
    void aThreadIsRemovedWithEverythingOtherPeopleHungOnIt() {
        Course course = courseUtilService.addEmptyCourse();
        User author = userUtilService.createAndSaveUser(TEST_PREFIX + "author");
        User other = userUtilService.createAndSaveUser(TEST_PREFIX + "other");
        Channel channel = conversationUtilService.createCourseWideChannel(course, TEST_PREFIX + "channel");

        // A thread the account started, with a reply and reactions left on it by somebody else.
        Post authoredThread = conversationUtilService.addMessageToConversation(author.getLogin(), channel);
        conversationUtilService.addThreadReplyWithReactionForUserToPost(other.getLogin(), authoredThread);
        conversationUtilService.addReactionForUserToPost(other.getLogin(), authoredThread);

        // A thread somebody else started, which the account only reacted to.
        Post foreignThread = conversationUtilService.addMessageToConversation(other.getLogin(), channel);
        conversationUtilService.addReactionForUserToPost(author.getLogin(), foreignThread);

        userOwnedContentDeletionService.deleteCommunicationContent(author.getId());

        assertThat(count(communicationDataCleanupRepository.countPosts(List.of(author.getId())))).as("the thread the account started is gone").isZero();
        assertThat(count(communicationDataCleanupRepository.countReactions(List.of(author.getId())))).as("so is what it left on other people's threads").isZero();
        assertThat(count(communicationDataCleanupRepository.countAnswerPosts(List.of(other.getId())))).as("the reply below that thread cannot outlive it").isZero();

        assertThat(count(communicationDataCleanupRepository.countPosts(List.of(other.getId())))).as("the thread the other account started is untouched").isOne();
        assertThat(count(communicationDataCleanupRepository.countReactions(List.of(other.getId())))).as("only the reaction on its own thread is left").isOne();
    }

    @Test
    void everyCleanupRunsForAnAccountThatOwnsNothing() {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "empty");
        long userId = user.getId();

        assertThatCode(() -> {
            assertThat(userOwnedContentDeletionService.deleteDataExports(userId)).isEmpty();
            userOwnedContentDeletionService.deleteTeams(userId);
            userOwnedContentDeletionService.deleteParticipations(userId);
            assertThat(userOwnedContentDeletionService.deleteExamAttendance(userId)).isEmpty();
            userOwnedContentDeletionService.deleteComplaints(userId);
            userOwnedContentDeletionService.deletePlagiarismCases(userId);
            userOwnedContentDeletionService.deleteCommunicationContent(userId);
            userOwnedContentDeletionService.deleteTutorParticipations(userId);
            userOwnedContentDeletionService.anonymiseScienceEvents(user.getLogin(), userId);
        }).doesNotThrowAnyException();
    }

    private static long count(List<UserReferenceCount> counts) {
        return counts.stream().mapToLong(UserReferenceCount::getCount).sum();
    }
}
