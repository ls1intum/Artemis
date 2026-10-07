package de.tum.cit.aet.artemis.communication.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.CreatedConversationMessage;
import de.tum.cit.aet.artemis.communication.domain.DefaultChannelType;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.dto.CreateAnswerPostDTO;
import de.tum.cit.aet.artemis.communication.dto.CreateFaqDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostConversationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.dto.ParentPostDTO;
import de.tum.cit.aet.artemis.communication.dto.ReactionDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.communication.service.AnswerMessageService;
import de.tum.cit.aet.artemis.communication.service.ConversationMessagingService;
import de.tum.cit.aet.artemis.communication.service.ReactionService;
import de.tum.cit.aet.artemis.communication.service.conversation.OneToOneChatService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

/**
 * Creates the course communication and the FAQs of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class CommunicationDemoApi implements AbstractApi {

    // Postings render every line break, so a paragraph that does not fit into one line of code continues with a trailing backslash, which joins the lines.

    /**
     * Title of the welcome announcement, which identifies it within the announcement channel, so it must stay stable.
     */
    private static final String WELCOME_TITLE = "Welcome to Introduction to Software Engineering";

    private static final String WELCOME = """
            Hello everyone, and welcome to **Introduction to Software Engineering**!

            This course teaches you how to design, build and evaluate software systems. It covers three topics:

            - **Software architecture:** architectural styles, quality attributes and the trade-offs between them
            - **Algorithms and complexity:** how to reason about efficiency with Big O notation
            - **Object-oriented modeling:** how to turn requirements into UML class diagrams

            Here is where you find everything:

            - **Lectures:** the slides, summaries and further reading of every lecture are in the *Lectures* tab.
            - **Exercises:** all exercises and their due dates are in the *Exercises* tab. You can submit as often as you like until the due date, so start early.
            - **Tutorials:** your weekly tutorial group with its time and room is in the *Tutorials* tab.
            - **Questions:** check the *FAQ* tab first. Ask about technical problems in *tech-support*, about an exercise in its channel, and about everything else \
            in *random*.

            We are looking forward to a great semester with you!""";

    // The first message of a thread identifies the thread within its channel, so the contents of CLONE_QUESTION, WORD_LIMIT_QUESTION, STUDY_GROUP_PROPOSAL and
    // TUTORIAL_GROUPS_INFO must stay stable.

    private static final String CLONE_QUESTION = "I cannot clone the repository of the programming exercise. Git keeps asking for a password and rejects my Artemis password with "
            + "\"Authentication failed\". What am I doing wrong?";

    private static final String CLONE_ANSWER = """
            Git does not accept your Artemis password, but you do not need it at all:

            1. Click *Code* on the exercise and copy the URL with *Token* selected. It contains an access token for your repository, so Git does not ask for a password.
            2. If you prefer SSH, add your public key under *SSH* in your settings and copy the SSH URL instead.
            3. If you would rather skip Git, click *Open code editor* and work in the browser. *Submit* hands in your code and runs the tests.

            Let me know if it still does not work!""";

    private static final String WORD_LIMIT_QUESTION = "How strict is the limit of 600 to 800 words? My draft has about 870 words, and I would rather not cut an argument "
            + "that matters.";

    private static final String WORD_LIMIT_ANSWER = "The 600 to 800 words are a guideline, not a hard limit: nobody counts single words, and a few words more do not cost "
            + "you points. *Clarity and structure* is part of the assessment, though, so if you are well above 800 words, take it as a hint to tighten your argument. A clear "
            + "recommendation for BookBarn matters more than covering every aspect.";

    private static final String WORD_LIMIT_FOLLOW_UP = "Do the headings and the list of references count towards the limit?";

    private static final String WORD_LIMIT_FOLLOW_UP_ANSWER = "No, only the text of the essay itself counts. Headings and references are welcome, they make your argument "
            + "easier to follow.";

    private static final String STUDY_GROUP_PROPOSAL = "Is anyone up for a study group for the practice exam? I was thinking of meeting in the library on Thursday afternoons and "
            + "going through the exercises together. Reply here if you want to join!";

    private static final String STUDY_GROUP_REPLY = "Count me in! I can bring a summary of the sorting algorithms and their complexities.";

    private static final String STUDY_GROUP_SECOND_REPLY = "Great idea, I am in too. Could we also practise class diagrams? Multiplicities still confuse me.";

    private static final String TUTORIAL_GROUPS_INFO = """
            **Tutorial groups**

            Tutorial groups meet every week in small groups, each led by a tutor. You discuss the exercises, practise the topics of the lecture on new examples, and ask \
            everything that is still unclear.

            - Find your group with its time and room in the *Tutorials* tab.
            - Look at the exercise of the week before the session, so that you can make the most of it.
            - Attendance is voluntary, but we strongly recommend it.

            See you there!""";

    private static final String FEEDBACK_QUESTION = "Hi! I just read your feedback on my essay *The Value of Code Reviews*, thank you! What should I focus on to get more points "
            + "in the next essay?";

    private static final String FEEDBACK_ANSWER = "Hi! Glad it helps. You found the right benefits and costs, so the main thing to work on is to apply them to the situation "
            + "of the task instead of stating them in general: which part of BookBarn's platform profits, and what exactly should the team change? For *Monolith or "
            + "Microservices?*, that means referring to BookBarn's symptoms in every argument. If anything is unclear, just ask in the channel of the exercise!";

    // The client stores an FAQ category as JSON together with the color of its badge.
    private static final String EXERCISES = faqCategory("Exercises", "#1b97ca");

    private static final String GRADING = faqCategory("Grading", "#ad5658");

    private static final String LECTURES = faqCategory("Lectures", "#9dca53");

    private static final String EXAMS = faqCategory("Exams", "#691b0b");

    private static final String TUTORIAL_GROUPS = faqCategory("Tutorial groups", "#0ab84f");

    private static final String SUBMISSION_FAQ = """
            You can work on a programming exercise in two ways:

            1. **In the browser:** open the exercise, click *Open code editor*, and click *Submit* to hand in your code.
            2. **In your IDE:** click *Code* to copy the URL of your repository, clone it, and commit and push your changes.

            Every submission is built and tested automatically, and you see the results of the tests a few moments later. You can submit as often as you like: the last \
            submission before the due date counts.""";

    private static final String RESULTS_FAQ = """
            That depends on how an exercise is assessed:

            - **Automatically:** quizzes and the tests of programming exercises give you feedback right after you submit, or when the quiz ends.
            - **Manually:** the tutors assess essays, class diagrams and file uploads after the due date. You see your result and the feedback as soon as the assessment \
            is complete.""";

    private static final String COMPLAINTS_FAQ = """
            If you think the assessment of a manually assessed exercise is wrong, open its result and click *Complain* within one week after the result was published. \
            Explain which part of the assessment you disagree with and why.

            Another tutor reviews your complaint and either accepts it and corrects your score, or rejects it with an explanation. You can submit up to three complaints in \
            this course, so save them for the cases that matter. If you only want to understand your result better, click *Request more feedback* instead.""";

    private static final String LECTURE_SLIDES_FAQ = "Open the *Lectures* tab and select a lecture. Its units contain the slides as a PDF, which you can view in the browser "
            + "or download, together with a summary of the lecture, further reading and the exercises that belong to it.";

    private static final String PRACTICE_EXAM_FAQ = """
            The practice exam is a test exam in the *Exams* tab. It has the format of the final exam, but it does not count towards your grade.

            You can start it whenever it is open and take it several times, and every attempt has its own working time. Use it to get to know the exam mode before the \
            final exam.""";

    private static final String TUTORIAL_GROUPS_FAQ = """
            Tutorial groups meet every week in small groups, each led by a tutor. You discuss the exercise of the week, practise the topics of the lecture on new examples, \
            and ask everything that is still unclear.

            You find your group with its time and room in the *Tutorials* tab. Attendance is voluntary, but we strongly recommend it: it is the best place to get feedback \
            on your own solutions.""";

    private static final Logger log = LoggerFactory.getLogger(CommunicationDemoApi.class);

    private final ConversationMessagingService conversationMessagingService;

    private final AnswerMessageService answerMessageService;

    private final ReactionService reactionService;

    private final OneToOneChatService oneToOneChatService;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    private final PostRepository postRepository;

    private final ChannelRepository channelRepository;

    private final OneToOneChatRepository oneToOneChatRepository;

    private final FaqRepository faqRepository;

    public CommunicationDemoApi(ConversationMessagingService conversationMessagingService, AnswerMessageService answerMessageService, ReactionService reactionService,
            OneToOneChatService oneToOneChatService, Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService, PostRepository postRepository,
            ChannelRepository channelRepository, OneToOneChatRepository oneToOneChatRepository, FaqRepository faqRepository) {
        this.conversationMessagingService = conversationMessagingService;
        this.answerMessageService = answerMessageService;
        this.reactionService = reactionService;
        this.oneToOneChatService = oneToOneChatService;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.postRepository = postRepository;
        this.channelRepository = channelRepository;
        this.oneToOneChatRepository = oneToOneChatRepository;
        this.faqRepository = faqRepository;
    }

    /**
     * Creates the communication of the demo course that does not exist yet: a welcome announcement of the instructor, threads with replies and reactions in the default
     * channels and in the channel of the discussed exercise, a pinned message about the tutorial groups, accepted FAQs, and direct messages between the demo student and the
     * demo tutor about the feedback on an essay.
     * <p>
     * Every message is sent as its author through the production messaging services, which also notify the members of the conversation. A thread is created with its replies
     * and reactions when its first message does not exist yet, and left alone otherwise, like the FAQs, which are identified by their question, and the direct messages, which
     * are only created as long as the demo student and the demo tutor have no conversation. The messages are dated to the time they are created and never revisited.
     *
     * @param course            the demo course.
     * @param students          the demo student followed by their ten classmates, who start and answer the threads.
     * @param tutor             the demo tutor, who answers the questions.
     * @param instructor        the demo instructor, who posts the announcement and the information about the tutorial groups.
     * @param discussedExercise the exercise whose channel discusses its word limit, or {@code null} to leave that thread out.
     */
    public void createDemo(Course course, List<User> students, User tutor, User instructor, @Nullable Exercise discussedExercise) {
        List<Post> existingPosts = postRepository.findAllByCourseId(course.getId());
        seedWelcome(course, existingPosts, students, instructor);
        seedCloneThread(course, existingPosts, students, tutor);
        Optional<Channel> exerciseChannel = Optional.ofNullable(discussedExercise).map(exercise -> channelRepository.findChannelByExerciseId(exercise.getId()));
        seedWordLimitThread(course, existingPosts, exerciseChannel, students, tutor);
        seedStudyGroupThread(course, existingPosts, students);
        seedTutorialGroupsInfo(course, existingPosts, students, instructor);
        seedFaqs(course);
        seedDirectMessages(course, students.getFirst(), tutor);
    }

    private void seedWelcome(Course course, List<Post> existingPosts, List<User> students, User instructor) {
        startThread(course, existingPosts, defaultChannel(course, DefaultChannelType.ANNOUNCEMENT), instructor, WELCOME_TITLE, WELCOME).ifPresent(welcome -> {
            react(course, welcome, students.get(1), "tada");
            react(course, welcome, students.get(4), "rocket");
            react(course, welcome, students.get(9), "+1");
        });
    }

    private void seedCloneThread(Course course, List<Post> existingPosts, List<User> students, User tutor) {
        User asker = students.get(2);
        startThread(course, existingPosts, defaultChannel(course, DefaultChannelType.TECH_SUPPORT), asker, null, CLONE_QUESTION).ifPresent(question -> {
            react(course, question, students.get(6), "+1");
            AnswerPost answer = answer(course, question, tutor, CLONE_ANSWER);
            markAsResolving(course, answer, asker);
            react(course, answer, asker, "pray");
            react(course, answer, students.get(8), "+1");
        });
    }

    private void seedWordLimitThread(Course course, List<Post> existingPosts, Optional<Channel> exerciseChannel, List<User> students, User tutor) {
        User asker = students.get(3);
        startThread(course, existingPosts, exerciseChannel, asker, null, WORD_LIMIT_QUESTION).ifPresent(question -> {
            AnswerPost answer = answer(course, question, tutor, WORD_LIMIT_ANSWER);
            react(course, answer, asker, "pray");
            react(course, answer, students.get(5), "+1");
            answer(course, question, students.get(4), WORD_LIMIT_FOLLOW_UP);
            AnswerPost followUpAnswer = answer(course, question, tutor, WORD_LIMIT_FOLLOW_UP_ANSWER);
            react(course, followUpAnswer, students.get(4), "+1");
        });
    }

    private void seedStudyGroupThread(Course course, List<Post> existingPosts, List<User> students) {
        User proposer = students.get(5);
        startThread(course, existingPosts, defaultChannel(course, DefaultChannelType.RANDOM), proposer, null, STUDY_GROUP_PROPOSAL).ifPresent(proposal -> {
            react(course, proposal, students.get(8), "raised_hands");
            react(course, proposal, students.get(9), "+1");
            react(course, answer(course, proposal, students.get(6), STUDY_GROUP_REPLY), proposer, "clap");
            react(course, answer(course, proposal, students.get(7), STUDY_GROUP_SECOND_REPLY), proposer, "+1");
        });
    }

    /**
     * Posts the information about the tutorial groups and pins it to the top of the channel, like an instructor does through
     * {@code ConversationMessageResource#updateDisplayPriority}.
     */
    private void seedTutorialGroupsInfo(Course course, List<Post> existingPosts, List<User> students, User instructor) {
        startThread(course, existingPosts, defaultChannel(course, DefaultChannelType.ORGANIZATION), instructor, null, TUTORIAL_GROUPS_INFO).ifPresent(info -> {
            SecurityUtils.runAs(instructor, () -> conversationMessagingService.changeDisplayPriority(course.getId(), info.getId(), DisplayPriority.PINNED));
            react(course, info, students.get(1), "eyes");
            react(course, info, students.get(10), "+1");
        });
    }

    /**
     * Creates the accepted FAQs that the course does not have yet. An FAQ is identified by its question within the course, so the questions must stay stable.
     */
    private void seedFaqs(Course course) {
        Set<String> existingQuestions = faqRepository.findAllByCourseId(course.getId()).stream().map(Faq::getQuestionTitle).collect(Collectors.toSet());
        // The FAQ tab lists the newest FAQ first, so the FAQs are created from the last to the first.
        seedFaq(course, existingQuestions, "What happens in tutorial groups?", TUTORIAL_GROUPS_FAQ, Set.of(TUTORIAL_GROUPS));
        seedFaq(course, existingQuestions, "How does the practice exam work?", PRACTICE_EXAM_FAQ, Set.of(EXAMS));
        seedFaq(course, existingQuestions, "Where can I find the lecture slides?", LECTURE_SLIDES_FAQ, Set.of(LECTURES));
        seedFaq(course, existingQuestions, "How do complaints work?", COMPLAINTS_FAQ, Set.of(GRADING));
        seedFaq(course, existingQuestions, "When are results published?", RESULTS_FAQ, Set.of(EXERCISES, GRADING));
        seedFaq(course, existingQuestions, "How do I submit a programming exercise?", SUBMISSION_FAQ, Set.of(EXERCISES));
    }

    /**
     * Creates the FAQ like {@code FaqResource#createFaq} creates an accepted FAQ of an instructor, apart from sending it to Iris: that is a synchronous request to Pyris,
     * which may not be reachable yet while the instance starts and would then abort the remaining demo communication. Instructors send the FAQs to Iris from the FAQ page.
     */
    private void seedFaq(Course course, Set<String> existingQuestions, String question, String answer, Set<String> categories) {
        if (existingQuestions.contains(question)) {
            log.debug("Demo FAQ '{}' already exists, skipping creation", question);
            return;
        }
        Faq faq = new CreateFaqDTO(course.getId(), question, answer, categories, FaqState.ACCEPTED).toEntity();
        faq.setCourse(course);
        Faq savedFaq = faqRepository.save(faq);
        searchableEntityWeaviateService.ifPresent(service -> service.upsertFaqAsync(FaqSearchableEntityDTO.fromFaq(savedFaq)));
        log.info("Created demo FAQ '{}' with id {}", question, savedFaq.getId());
    }

    /**
     * Starts the direct messages between the demo student and the demo tutor like {@code OneToOneChatResource#startOneToOneChat}, unless the two already have a conversation.
     */
    private void seedDirectMessages(Course course, User student, User tutor) {
        if (oneToOneChatRepository.findIdOfChatInCourseBetweenUsers(course.getId(), student.getLogin(), tutor.getLogin()) != null) {
            log.debug("Demo direct messages between '{}' and '{}' already exist, skipping creation", student.getLogin(), tutor.getLogin());
            return;
        }
        OneToOneChat chat = SecurityUtils.runAs(student, () -> oneToOneChatService.startOneToOneChat(course, student, tutor));
        send(course, chat, student, null, FEEDBACK_QUESTION);
        send(course, chat, tutor, null, FEEDBACK_ANSWER);
        log.info("Created demo direct messages between '{}' and '{}' with id {}", student.getLogin(), tutor.getLogin(), chat.getId());
    }

    private Optional<Channel> defaultChannel(Course course, DefaultChannelType type) {
        return channelRepository.findChannelByCourseIdAndName(course.getId(), type.getName()).stream().findFirst();
    }

    /**
     * Sends the first message of a demo thread, unless its channel no longer exists or already contains the thread. A thread is identified by the title of its first message,
     * which only announcements have, and otherwise by its content.
     *
     * @return the first message if this call sent it, so that the rest of the thread is created below it.
     */
    private Optional<Post> startThread(Course course, List<Post> existingPosts, Optional<Channel> channel, User author, @Nullable String title, String content) {
        if (channel.isEmpty()) {
            return Optional.empty();
        }
        Long channelId = channel.get().getId();
        boolean exists = existingPosts.stream()
                .anyMatch(post -> channelId.equals(post.getConversation().getId()) && (title != null ? title.equals(post.getTitle()) : content.equals(post.getContent())));
        if (exists) {
            log.debug("Demo thread in channel '{}' already exists, skipping creation", channel.get().getName());
            return Optional.empty();
        }
        Post post = send(course, channel.get(), author, title, content);
        log.info("Created demo thread in channel '{}' with id {}", channel.get().getName(), post.getId());
        return Optional.of(post);
    }

    /**
     * Sends a message as its author like {@code ConversationMessageResource#createMessage}, which also notifies the members of the conversation.
     */
    private Post send(Course course, Conversation conversation, User author, @Nullable String title, String content) {
        return SecurityUtils.runAs(author, () -> {
            CreatedConversationMessage message = conversationMessagingService.createMessage(course.getId(),
                    new CreatePostDTO(content, title, false, new CreatePostConversationDTO(conversation.getId())));
            conversationMessagingService.notifyAboutMessageCreation(message);
            return message.messageWithHiddenDetails();
        });
    }

    /**
     * Replies to a message as its author like {@code AnswerMessageResource#createAnswerMessage}.
     */
    private AnswerPost answer(Course course, Post post, User author, String content) {
        return SecurityUtils.runAs(author, () -> answerMessageService.createAnswerMessage(course.getId(), new CreateAnswerPostDTO(content, new ParentPostDTO(post.getId()))));
    }

    /**
     * Marks the reply as the one that resolves its thread like {@code AnswerMessageResource#updateAnswerMessage}, which the author of the thread may do.
     */
    private void markAsResolving(Course course, AnswerPost answer, User threadAuthor) {
        SecurityUtils.runAs(threadAuthor,
                () -> answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), answer.getContent(), null, true)));
    }

    /**
     * Reacts to a message or a reply like {@code ReactionResource#createReaction}, with the id that the emoji picker of the client sends for the emoji.
     */
    private void react(Course course, Posting posting, User user, String emojiId) {
        PostingType type = posting instanceof AnswerPost ? PostingType.ANSWER : PostingType.POST;
        SecurityUtils.runAs(user, () -> reactionService.createReaction(course.getId(), new ReactionDTO(null, null, null, emojiId, posting.getId(), type)));
    }

    private static String faqCategory(String name, String color) {
        return "{\"color\":\"%s\",\"category\":\"%s\"}".formatted(color, name);
    }
}
