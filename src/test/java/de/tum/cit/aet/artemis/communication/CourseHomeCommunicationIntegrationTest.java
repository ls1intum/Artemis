package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.CourseHomeQuestionState;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.CourseHomeCommunicationService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.FaqTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = CourseHomeCommunicationIntegrationTest.PREFIX + "student1", roles = "USER")
class CourseHomeCommunicationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homequestions";

    @Autowired
    private CourseHomeCommunicationService home;

    @Autowired
    private ChannelRepository channels;

    @Autowired
    private PostTestRepository posts;

    @Autowired
    private AnswerPostRepository answers;

    @Autowired
    private ConversationParticipantTestRepository participants;

    @Autowired
    private FaqTestRepository faqs;

    private Course course;

    private User student;

    private User other;

    private Channel channel;

    private ConversationParticipant membership;

    private ZonedDateTime baseline;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 2, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        student = userUtilService.getUserByLogin(PREFIX + "student1");
        other = userUtilService.getUserByLogin(PREFIX + "student2");
        baseline = ZonedDateTime.now().minusHours(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        channel = channel("questions", true, false, false);
        membership = ConversationParticipant.createWithDefaultValues(student, channel);
        membership.setLastRead(baseline);
        membership = participants.saveAndFlush(membership);
    }

    @Test
    void ownFollowUpDoesNotAnswerQuestion() {
        var root = post(channel, student, "How does iteration work?");
        answer(root, student, baseline.plusMinutes(1));
        var result = load();
        assertThat(result.questions().items()).singleElement().satisfies(i -> {
            assertThat(i.state()).isEqualTo(CourseHomeQuestionState.AWAITING_REPLY);
            assertThat(i.totalReplies()).isEqualTo(1);
            assertThat(i.unreadReplies()).isZero();
        });
    }

    @Test
    void resolvedQuestionCanHaveUnreadReply() {
        var root = post(channel, student, "Why?");
        root.setResolved(true);
        posts.saveAndFlush(root);
        var reply = answer(root, other, baseline.plusMinutes(1));
        assertThat(load().questions().items()).singleElement().satisfies(i -> {
            assertThat(i.state()).isEqualTo(CourseHomeQuestionState.RESOLVED);
            assertThat(i.unreadReplies()).isEqualTo(1);
            assertThat(i.focusReplyId()).isEqualTo(reply.getId());
        });
    }

    @Test
    void unknownLastReadDoesNotBecomeZero() {
        membership.setLastRead(null);
        participants.saveAndFlush(membership);
        var root = post(channel, student, "Why?");
        answer(root, other, baseline.plusMinutes(1));
        assertThat(load().questions().items().getFirst().unreadReplies()).isNull();
    }

    @Test
    void replyAtLastReadIsRead() {
        var root = post(channel, student, "Why?");
        answer(root, other, baseline);
        assertThat(load().questions().items().getFirst().unreadReplies()).isZero();
    }

    @Test
    void homeSummaryDoesNotMarkConversationRead() {
        post(channel, student, "Why?");
        load();
        assertThat(participants.findById(membership.getId()).orElseThrow().getLastRead().toInstant())
                .isEqualTo(baseline.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void excludesOtherAuthorsPrivateUnjoinedAndAnnouncementRoots() {
        post(channel, other, "Other person's question");
        post(channel("private", false, false, false), student, "Old private question");
        post(channel("public", true, false, false), student, "Left public channel");
        post(channel("announcements", true, true, true), student, "Announcement");
        assertThat(load().questions().items()).isEmpty();
    }

    @Test
    void removalRevokesAccess() {
        post(channel, student, "Question");
        participants.deleteById(membership.getId());
        assertThat(load().questions().items()).isEmpty();
    }

    @Test
    void knownEmptyAndUnreadOrderingAreIndependentOfResolution() {
        var unread = post(channel, student, "Unread");
        answer(unread, other, baseline.plusMinutes(1));
        var recent = post(channel, student, "Recent");
        answer(recent, other, baseline.minusMinutes(1));
        assertThat(load().questions().items()).extracting(i -> i.postId()).startsWith(unread.getId());
    }

    @Test
    void onlyPinnedAnnouncementsAndAcceptedFaqsAreShown() {
        var announcementChannel = channel("news", true, true, true);
        var pinned = post(announcementChannel, other, "Important update");
        pinned.setDisplayPriority(DisplayPriority.PINNED);
        posts.saveAndFlush(pinned);
        post(announcementChannel, other, "Unpinned");
        var accepted = faq(FaqState.ACCEPTED);
        faq(FaqState.PROPOSED);
        faq(FaqState.REJECTED);
        var result = load();
        assertThat(result.announcement().target().id()).isEqualTo(pinned.getId());
        assertThat(result.faqs()).extracting(i -> i.target().id()).containsExactly(accepted.getId());
    }

    @Test
    void titleFallbackIsPlainTextAndBounded() {
        var root = post(channel, student, null);
        root.setContent("<b>Question</b> " + "x".repeat(300));
        posts.saveAndFlush(root);
        var title = load().questions().items().getFirst().title();
        assertThat(title).startsWith("Question").doesNotContain("<b>").hasSizeLessThanOrEqualTo(160);
    }

    @Autowired
    private de.tum.cit.aet.artemis.communication.test_repository.OneToOneChatTestRepository directChats;

    @Autowired
    private de.tum.cit.aet.artemis.text.util.TextExerciseUtilService exercises;

    @Autowired
    private de.tum.cit.aet.artemis.communication.test_repository.FaqTestRepository faqDates;

    @Test
    void directMessagesAndUnreleasedChannelsStayOutOfHome() {
        var dm = new de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat();
        dm.setCourse(course);
        dm.setCreator(student);
        dm = directChats.saveAndFlush(dm);
        participants.saveAndFlush(ConversationParticipant.createWithDefaultValues(student, dm));
        post(dm, student, "Private direct message");
        var hidden = channel("unreleased", true, true, false);
        hidden.setExercise(exercises.createIndividualTextExercise(course, baseline.plusDays(1), baseline.plusDays(2), baseline.plusDays(3)));
        channels.saveAndFlush(hidden);
        post(hidden, student, "Future exercise");
        assertThat(load().questions().items()).isEmpty();
    }

    @Test
    void unverifiedIrisReplyDoesNotAnswerAStudentQuestion() {
        var root = post(channel, student, "Question");
        var reply = answer(root, userUtilService.getOrCreateIrisBotUser(), baseline.plusMinutes(1));
        reply.setVerified(false);
        answers.saveAndFlush(reply);
        var item = load().questions().items().getFirst();
        assertThat(item.state()).isEqualTo(CourseHomeQuestionState.AWAITING_REPLY);
        assertThat(item.unreadReplies()).isZero();
        assertThat(item.totalReplies()).isZero();
    }

    @Test
    void disabledCommunicationSuppressesQuestionsButKeepsPublishedFaqs() {
        post(channel, student, "Question");
        faq(FaqState.ACCEPTED);
        course.setCourseInformationSharingConfiguration(de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration.DISABLED);
        courseRepository.saveAndFlush(course);
        var result = load();
        assertThat(result.questions().status().name()).isEqualTo("DISABLED");
        assertThat(result.questions().items()).isEmpty();
        assertThat(result.faqs()).hasSize(1);
    }

    @Test
    void anOlderFaqAcceptedRecentlyIsNew() {
        var old = faq(FaqState.PROPOSED);
        faqDates.setCreationDate(old.getId(), baseline.minusDays(30).toInstant());
        old = faqs.findById(old.getId()).orElseThrow();
        old.setFaqState(FaqState.ACCEPTED);
        faqs.saveAndFlush(old);
        assertThat(load().faqs()).extracting(i -> i.target().id()).containsExactly(old.getId());
    }

    private de.tum.cit.aet.artemis.communication.dto.CourseHomeCommunicationDTO load() {
        return home.load(course.getId(), student, Instant.now(), baseline.minusDays(7).toInstant());
    }

    private Channel channel(String name, boolean publicChannel, boolean courseWide, boolean announcement) {
        var c = new Channel();
        c.setCourse(course);
        c.setCreator(other);
        c.setName(name);
        c.setIsPublic(publicChannel);
        c.setIsCourseWide(courseWide);
        c.setIsAnnouncementChannel(announcement);
        return channels.saveAndFlush(c);
    }

    private Post post(de.tum.cit.aet.artemis.communication.domain.conversation.Conversation c, User author, String title) {
        var p = new Post();
        p.setConversation(c);
        p.setAuthor(author);
        p.setTitle(title);
        p.setContent("Question content");
        p.setVisibleForStudents(true);
        return posts.saveAndFlush(p);
    }

    private AnswerPost answer(Post p, User author, ZonedDateTime date) {
        var a = new AnswerPost();
        a.setPost(p);
        a.setAuthor(author);
        a.setContent("A reply");
        a.setCreationDate(date);
        return answers.saveAndFlush(a);
    }

    private Faq faq(FaqState state) {
        var f = new Faq();
        f.setCourse(course);
        f.setFaqState(state);
        f.setQuestionTitle("How can I practice?");
        f.setQuestionAnswer("Try an exercise");
        return faqs.saveAndFlush(f);
    }
}
