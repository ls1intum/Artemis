package de.tum.cit.aet.artemis.demo;

import static de.tum.cit.aet.artemis.account.api.AccountDemoApi.DEMO_INSTRUCTOR_LOGIN;
import static de.tum.cit.aet.artemis.account.api.AccountDemoApi.DEMO_PEER_LOGIN_PREFIX;
import static de.tum.cit.aet.artemis.account.api.AccountDemoApi.DEMO_STUDENT_LOGIN;
import static de.tum.cit.aet.artemis.account.api.AccountDemoApi.DEMO_TUTOR_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseAvailableTabsService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the course communication and the FAQs that the {@code demo} profile seeds into the demo course.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, every test seeds the whole demo course and must stay correct whatever ran before it, because the demo course persists in the
 * shared test database.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoCommunicationSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // The questions identify the demo FAQs on every startup, so they are spelled out here: changing one would create the FAQ a second time on existing demo instances.
    private static final List<String> FAQ_QUESTIONS = List.of("How do I submit a programming exercise?", "When are results published?", "How do complaints work?",
            "Where can I find the lecture slides?", "How does the practice exam work?", "What happens in tutorial groups?");

    private static final String DISCUSSED_EXERCISE_TITLE = "Essay: Monolith or Microservices?";

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private OneToOneChatRepository oneToOneChatRepository;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private CourseAvailableTabsService courseAvailableTabsService;

    @Test
    void seedsWelcomeAnnouncementOfTheInstructor() {
        seed();

        assertThat(messages(channel("announcement"))).filteredOn(post -> "Welcome to Introduction to Software Engineering".equals(post.getTitle()))
                .as("the instructor welcomes the students with an announcement").singleElement().satisfies(welcome -> {
                    assertThat(welcome.getAuthor().getLogin()).isEqualTo(DEMO_INSTRUCTOR_LOGIN);
                    assertThat(welcome.getContent()).as("the announcement says where to find the course content").contains("*Lectures*", "*Exercises*", "*Tutorials*");
                    assertThat(reactingLogins(welcome)).as("classmates react to the announcement").isNotEmpty().allMatch(login -> login.startsWith(DEMO_PEER_LOGIN_PREFIX));
                });
    }

    @Test
    void seedsTechSupportThreadThatTheTutorResolves() {
        seed();

        Post question = thread(channel("tech-support"), peer(2));
        assertThat(question.getContent()).as("a classmate cannot clone the repository").contains("clone");
        assertThat(question.isResolved()).as("the thread is resolved").isTrue();
        assertThat(reactingLogins(question)).as("another classmate has the same problem").isNotEmpty();
        assertThat(question.getAnswers()).as("the tutor explains how to get the code").singleElement().satisfies(answer -> {
            assertThat(answer.getAuthor().getLogin()).isEqualTo(DEMO_TUTOR_LOGIN);
            assertThat(answer.doesResolvePost()).as("the answer is marked as resolving the thread").isTrue();
            assertThat(reactingLogins(answer)).as("the asker thanks the tutor").contains(peer(2));
        });
    }

    @Test
    void seedsWordLimitDiscussionInTheChannelOfTheEssay() {
        seed();

        Exercise essay = exerciseRepository.findAllExercisesByCourseId(demoCourse().getId()).stream().filter(exercise -> DISCUSSED_EXERCISE_TITLE.equals(exercise.getTitle()))
                .findFirst().orElseThrow();
        Post question = thread(channelRepository.findChannelByExerciseId(essay.getId()), peer(3));
        assertThat(question.getContent()).as("a classmate asks about the word limit of the essay").contains("800 words");
        List<AnswerPost> answers = inOrder(question.getAnswers());
        assertThat(answers).extracting(answer -> answer.getAuthor().getLogin()).as("the tutor answers, another classmate follows up and the tutor answers again")
                .containsExactly(DEMO_TUTOR_LOGIN, peer(4), DEMO_TUTOR_LOGIN);
        assertThat(reactingLogins(answers.getFirst())).as("the asker thanks the tutor").contains(peer(3));
    }

    @Test
    void seedsStudyGroupProposalThatClassmatesJoin() {
        seed();

        Post proposal = thread(channel("random"), peer(5));
        assertThat(proposal.getContent()).contains("study group", "practice exam");
        assertThat(inOrder(proposal.getAnswers())).extracting(reply -> reply.getAuthor().getLogin()).as("two classmates join").containsExactly(peer(6), peer(7));
        assertThat(reactingLogins(proposal)).as("further classmates react to the proposal").hasSizeGreaterThanOrEqualTo(2);
        assertThat(proposal.getAnswers()).as("the proposer reacts to every reply").allSatisfy(reply -> assertThat(reactingLogins(reply)).contains(peer(5)));
    }

    @Test
    void pinsTheInformationAboutTutorialGroups() {
        seed();

        Post information = thread(channel("organization"), DEMO_INSTRUCTOR_LOGIN);
        assertThat(information.getDisplayPriority()).as("the information stays on top of the channel").isEqualTo(DisplayPriority.PINNED);
        assertThat(information.getContent()).as("the information says where to find the tutorial groups").contains("*Tutorials*");
    }

    @Test
    void seedsDirectMessagesBetweenDemoStudentAndTutor() {
        seed();

        Long chatId = oneToOneChatRepository.findIdOfChatInCourseBetweenUsers(demoCourse().getId(), DEMO_STUDENT_LOGIN, DEMO_TUTOR_LOGIN);
        assertThat(chatId).as("the demo student and the demo tutor have a conversation").isNotNull();
        List<Post> messages = messages(chatId);
        assertThat(messages).extracting(message -> message.getAuthor().getLogin()).as("the student asks and the tutor answers").containsExactly(DEMO_STUDENT_LOGIN,
                DEMO_TUTOR_LOGIN);
        assertThat(messages.getFirst().getContent()).as("the student asks about the feedback on their essay").contains("The Value of Code Reviews");
    }

    @Test
    void seedsAcceptedFaqsThatFillTheFaqTab() {
        seed();

        List<Faq> faqs = faqRepository.findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(demoCourse().getId(), FaqState.ACCEPTED);
        assertThat(faqs).extracting(Faq::getQuestionTitle).as("students see every demo FAQ").containsAll(FAQ_QUESTIONS);
        assertThat(faqs).filteredOn(faq -> FAQ_QUESTIONS.contains(faq.getQuestionTitle())).allSatisfy(faq -> {
            assertThat(faq.getQuestionAnswer()).as("%s is answered", faq.getQuestionTitle()).isNotBlank();
            // The client parses every category as the JSON it stores, so a category in any other format breaks the FAQ tab.
            assertThat(faq.getCategories()).as("%s has categories in the format of the client", faq.getQuestionTitle()).isNotEmpty()
                    .allMatch(category -> category.matches("\\{\"color\":\"#[0-9a-f]{6}\",\"category\":\"[^\"]+\"}"));
        });
        User demoStudent = userTestRepository.findOneByLogin(DEMO_STUDENT_LOGIN).orElseThrow();
        assertThat(courseAvailableTabsService.getAvailableTabs(demoCourse(), demoStudent).faq()).as("the FAQs fill the FAQ tab of the course").isTrue();
    }

    @Test
    void seedingTwiceCreatesNoCommunicationContent() {
        seed();
        CommunicationSnapshot afterFirstRun = snapshotCommunication();

        seed();

        assertThat(snapshotCommunication()).as("seeding again neither creates nor replaces messages, replies, reactions, FAQs or conversations").isEqualTo(afterFirstRun);
    }

    @Test
    void recreatesDeletedFaq() {
        seed();
        Faq deletedFaq = faq("How do complaints work?");
        faqRepository.deleteById(deletedFaq.getId());
        Set<Long> remainingFaqIds = ids(faqRepository.findAllByCourseId(demoCourse().getId()));

        seed();

        Faq recreatedFaq = faq("How do complaints work?");
        assertThat(recreatedFaq.getId()).as("the deleted FAQ is created again").isNotEqualTo(deletedFaq.getId());
        assertThat(recreatedFaq.getQuestionAnswer()).as("the FAQ is recreated as it was seeded").isEqualTo(deletedFaq.getQuestionAnswer());
        assertThat(recreatedFaq.getCategories()).as("the FAQ is recreated as it was seeded").isEqualTo(deletedFaq.getCategories());
        assertThat(recreatedFaq.getFaqState()).isEqualTo(FaqState.ACCEPTED);
        assertThat(ids(faqRepository.findAllByCourseId(demoCourse().getId()))).as("the other FAQs are left alone").containsAll(remainingFaqIds).hasSize(remainingFaqIds.size() + 1);
    }

    private void seed() {
        demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).getFirst();
    }

    private static String peer(int number) {
        return DEMO_PEER_LOGIN_PREFIX + number;
    }

    private Channel channel(String name) {
        return channelRepository.findChannelByCourseIdAndName(demoCourse().getId(), name).iterator().next();
    }

    private List<Post> messages(Channel channel) {
        return messages(channel.getId());
    }

    /**
     * The messages of the conversation in the order in which they were sent.
     */
    private List<Post> messages(long conversationId) {
        return inOrder(postRepository.findAllByCourseId(demoCourse().getId()).stream().filter(post -> post.getConversation().getId() == conversationId).toList());
    }

    /**
     * The thread that the given user started in the channel.
     */
    private Post thread(Channel channel, String authorLogin) {
        List<Post> threads = messages(channel).stream().filter(post -> authorLogin.equals(post.getAuthor().getLogin())).toList();
        assertThat(threads).as("%s started exactly one thread in %s", authorLogin, channel.getName()).hasSize(1);
        return threads.getFirst();
    }

    /**
     * Orders postings by their ids, which follow the order in which they were created even when two of them share a creation date.
     */
    private static <T extends Posting> List<T> inOrder(Collection<T> postings) {
        return postings.stream().sorted(Comparator.comparing(DomainObject::getId)).toList();
    }

    private static List<String> reactingLogins(Posting posting) {
        return posting.getReactions().stream().map(reaction -> reaction.getUser().getLogin()).toList();
    }

    private Faq faq(String question) {
        return faqRepository.findAllByCourseId(demoCourse().getId()).stream().filter(faq -> question.equals(faq.getQuestionTitle())).findFirst().orElseThrow();
    }

    private static Set<Long> ids(Collection<? extends DomainObject> entities) {
        return entities.stream().map(DomainObject::getId).collect(Collectors.toSet());
    }

    /**
     * Captures the identities of the communication of the demo course, so that replaced records are detected as well as new ones.
     */
    private CommunicationSnapshot snapshotCommunication() {
        long courseId = demoCourse().getId();
        List<Post> posts = postRepository.findAllByCourseId(courseId);
        List<AnswerPost> answers = posts.stream().flatMap(post -> post.getAnswers().stream()).toList();
        Set<Long> reactionIds = ids(Stream.concat(posts.stream(), answers.stream()).flatMap(posting -> posting.getReactions().stream()).toList());
        Set<Long> conversationIds = Stream.concat(channelRepository.findChannelsByCourseId(courseId).stream().map(Channel::getId),
                Stream.of(oneToOneChatRepository.findIdOfChatInCourseBetweenUsers(courseId, DEMO_STUDENT_LOGIN, DEMO_TUTOR_LOGIN))).collect(Collectors.toSet());
        return new CommunicationSnapshot(ids(posts), ids(answers), reactionIds, ids(faqRepository.findAllByCourseId(courseId)), conversationIds);
    }

    private record CommunicationSnapshot(Set<Long> postIds, Set<Long> answerIds, Set<Long> reactionIds, Set<Long> faqIds, Set<Long> conversationIds) {
    }
}
