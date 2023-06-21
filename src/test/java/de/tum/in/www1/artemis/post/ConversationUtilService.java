package de.tum.in.www1.artemis.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.*;
import de.tum.in.www1.artemis.domain.metis.conversation.*;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.metis.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to conversations for use in integration tests.
 */
@Service
public class ConversationUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    private static int dayCount = 1;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private OneToOneChatRepository oneToOneChatRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    public Course createCourseWithPostsDisabled() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        return courseRepo.save(course);
    }

    public List<Post> createPostsWithinCourse(String userPrefix) {

        List<Exercise> testExercises = new ArrayList<>();
        List<Lecture> testLectures = new ArrayList<>();

        Course course1 = courseUtilService.createCourse();
        for (int i = 0; i < 2; i++) {
            TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
            course1.addExercises(textExercise);
            textExercise = exerciseRepo.save(textExercise);
            testExercises.add(textExercise);

            Lecture lecture = LectureFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
            course1.addLectures(lecture);
            lecture = lectureRepo.save(lecture);
            testLectures.add(lecture);
        }

        courseRepo.save(course1);

        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(testExercises.get(0));
        plagiarismCase.setStudent(userUtilService.getUserByLogin(userPrefix + "student1"));
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        List<Post> posts = new ArrayList<>();

        // add posts to exercise
        posts.addAll(createBasicPosts(testExercises.toArray(Exercise[]::new), userPrefix));

        // add posts to lecture
        posts.addAll(createBasicPosts(testLectures.toArray(Lecture[]::new), userPrefix));

        // add post to plagiarismCase
        posts.add(createBasicPost(plagiarismCase, userPrefix));

        // add posts to course with different course-wide contexts provided in input array
        CourseWideContext[] courseWideContexts = new CourseWideContext[] { CourseWideContext.ORGANIZATION, CourseWideContext.RANDOM, CourseWideContext.TECH_SUPPORT,
                CourseWideContext.ANNOUNCEMENT };
        posts.addAll(createBasicPosts(course1, courseWideContexts, userPrefix));
        posts.addAll(createBasicPosts(createOneToOneChat(course1, userPrefix), userPrefix));

        return posts;
    }

    public List<Post> createPostsWithAnswersAndReactionsAndConversation(Course course, User student1, User student2, int numberOfPosts, String userPrefix) {
        var chat = new OneToOneChat();
        chat.setCourse(course);
        chat.setCreator(student1);
        chat.setCreationDate(ZonedDateTime.now());
        chat.setLastMessageDate(ZonedDateTime.now());
        chat = oneToOneChatRepository.save(chat);
        var participant1 = new ConversationParticipant();
        participant1.setConversation(chat);
        participant1.setUser(student1);
        participant1.setUnreadMessagesCount(0L);
        participant1.setLastRead(ZonedDateTime.now().minusYears(2));
        conversationParticipantRepository.save(participant1);
        var participant2 = new ConversationParticipant();
        participant2.setConversation(chat);
        participant2.setUser(student2);
        participant2.setUnreadMessagesCount(0L);
        participant2.setLastRead(ZonedDateTime.now().minusYears(2));
        conversationParticipantRepository.save(participant2);
        chat = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(chat.getId()).get();

        var posts = new ArrayList<Post>();
        for (int i = 0; i < numberOfPosts; i++) {
            var post = new Post();
            post.setAuthor(student1);
            post.setDisplayPriority(DisplayPriority.NONE);
            post.setConversation(chat);
            post = postRepository.save(post);
            posts.add(post);
        }

        // add many answers for all posts in conversation
        for (var post : posts) {
            post.setAnswers(createBasicAnswers(post, userPrefix));
            postRepository.save(post);
        }

        // add many reactions for all posts in conversation
        for (var post : posts) {
            Reaction reaction = new Reaction();
            reaction.setEmojiId("smiley");
            reaction.setPost(post);
            reaction.setUser(student1);
            reactionRepository.save(reaction);
            post.setReactions(Set.of(reaction));
            postRepository.save(post);
        }
        return posts;
    }

    public List<Post> createPostsWithAnswerPostsWithinCourse(String userPrefix) {
        List<Post> posts = createPostsWithinCourse(userPrefix);

        // add answer for one post in each context (lecture, exercise, course-wide, conversation)
        Post lecturePost = posts.stream().filter(coursePost -> coursePost.getLecture() != null).findFirst().orElseThrow();
        lecturePost.setAnswers(createBasicAnswers(lecturePost, userPrefix));
        lecturePost.getAnswers().addAll(createBasicAnswers(lecturePost, userPrefix));
        postRepository.save(lecturePost);

        Post exercisePost = posts.stream().filter(coursePost -> coursePost.getExercise() != null).findFirst().orElseThrow();
        exercisePost.setAnswers(createBasicAnswers(exercisePost, userPrefix));
        postRepository.save(exercisePost);

        // resolved post
        Post courseWidePost = posts.stream().filter(coursePost -> coursePost.getCourseWideContext() != null).findFirst().orElseThrow();
        courseWidePost.setAnswers(createBasicAnswersThatResolves(courseWidePost, userPrefix));
        postRepository.save(courseWidePost);

        Post conversationPost = posts.stream().filter(coursePost -> coursePost.getConversation() != null).findFirst().orElseThrow();
        conversationPost.setAnswers(createBasicAnswers(conversationPost, userPrefix));
        postRepository.save(conversationPost);

        return posts;
    }

    private List<Post> createBasicPosts(Exercise[] exerciseContexts, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (Exercise exerciseContext : exerciseContexts) {
            for (int i = 0; i < 4; i++) {
                Post postToAdd = createBasicPost(i, userPrefix + "student");
                postToAdd.setExercise(exerciseContext);
                postRepository.save(postToAdd);
                posts.add(postToAdd);
            }
        }

        return posts;
    }

    private List<Post> createBasicPosts(Lecture[] lectureContexts, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (Lecture lectureContext : lectureContexts) {
            for (int i = 0; i < 4; i++) {
                Post postToAdd = createBasicPost(i, userPrefix + "tutor");
                postToAdd.setLecture(lectureContext);
                postRepository.save(postToAdd);
                posts.add(postToAdd);
            }
        }
        return posts;
    }

    private List<Post> createBasicPosts(Course courseContext, CourseWideContext[] courseWideContexts, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < courseWideContexts.length; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "editor");
            postToAdd.setCourse(courseContext);
            postToAdd.setCourseWideContext(courseWideContexts[i]);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private Post createBasicPost(PlagiarismCase plagiarismCase, String userPrefix) {
        Post postToAdd = createBasicPost(0, userPrefix + "instructor");
        postToAdd.setPlagiarismCase(plagiarismCase);
        postToAdd.getPlagiarismCase().setExercise(null);
        return postRepository.save(postToAdd);
    }

    private Post createBasicPost(Integer i, String usernamePrefix) {
        Post post = new Post();
        post.setTitle(String.format("Title Post %s", (i + 1)));
        post.setContent(String.format("Content Post %s", (i + 1)));
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setAuthor(userUtilService.getUserByLoginWithoutAuthorities(String.format("%s%s", usernamePrefix, (i + 1))));
        post.setCreationDate(ZonedDateTime.of(2015, 11, dayCount, 23, 45, 59, 1234, ZoneId.of("UTC")));
        String tag = String.format("Tag %s", (i + 1));
        Set<String> tags = new HashSet<>();
        tags.add(tag);
        post.setTags(tags);

        dayCount = (dayCount % 25) + 1;
        return post;
    }

    private List<Post> createBasicPosts(Conversation conversation, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "tutor");
            postToAdd.setConversation(conversation);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private Set<AnswerPost> createBasicAnswers(Post post, String userPrefix) {
        Set<AnswerPost> answerPosts = new HashSet<>();
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(post.getContent() + " Answer");
        answerPost.setAuthor(userUtilService.getUserByLoginWithoutAuthorities(userPrefix + "student1"));
        answerPost.setPost(post);
        answerPosts.add(answerPost);
        answerPostRepository.save(answerPost);
        post.setAnswerCount(post.getAnswerCount() + 1);
        return answerPosts;
    }

    private Set<AnswerPost> createBasicAnswersThatResolves(Post post, String userPrefix) {
        Set<AnswerPost> answerPosts = new HashSet<>();
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(post.getContent() + " Answer");
        answerPost.setAuthor(userUtilService.getUserByLoginWithoutAuthorities(userPrefix + "student1"));
        answerPost.setPost(post);
        answerPost.setResolvesPost(true);
        answerPosts.add(answerPost);
        answerPostRepository.save(answerPost);
        post.setAnswerCount(post.getAnswerCount() + 1);
        post.setResolved(true);
        return answerPosts;
    }

    public <T extends Posting> void assertSensitiveInformationHidden(@NotNull List<T> postings) {
        for (Posting posting : postings) {
            assertSensitiveInformationHidden(posting);
        }
    }

    public void assertSensitiveInformationHidden(@NotNull Posting posting) {
        if (posting.getAuthor() != null) {
            assertThat(posting.getAuthor().getEmail()).isNull();
            assertThat(posting.getAuthor().getLogin()).isNull();
            assertThat(posting.getAuthor().getRegistrationNumber()).isNull();
        }
    }

    public void assertSensitiveInformationHidden(@NotNull Reaction reaction) {
        if (reaction.getUser() != null) {
            assertThat(reaction.getUser().getEmail()).isNull();
            assertThat(reaction.getUser().getLogin()).isNull();
            assertThat(reaction.getUser().getRegistrationNumber()).isNull();
        }
    }

    public Conversation createOneToOneChat(Course course, String userPrefix) {
        Conversation conversation = new OneToOneChat();
        conversation.setCourse(course);
        conversation = conversationRepository.save(conversation);

        List<ConversationParticipant> conversationParticipants = new ArrayList<>();
        conversationParticipants.add(createConversationParticipant(conversation, userPrefix + "tutor1"));
        conversationParticipants.add(createConversationParticipant(conversation, userPrefix + "tutor2"));

        conversation.setConversationParticipants(new HashSet<>(conversationParticipants));
        return conversationRepository.save(conversation);
    }

    public static Channel createChannel(Course course, String channelName) {
        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName(channelName);
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setDescription("Test channel");
        return channel;
    }

    private ConversationParticipant createConversationParticipant(Conversation conversation, String userName) {
        ConversationParticipant conversationParticipant = new ConversationParticipant();
        conversationParticipant.setConversation(conversation);
        conversationParticipant.setLastRead(conversation.getLastMessageDate());
        conversationParticipant.setUser(userUtilService.getUserByLogin(userName));

        return conversationParticipantRepository.save(conversationParticipant);
    }

    public Conversation addMessageWithReplyAndReactionInGroupChatOfCourseForUser(String login, Course course, String messageText) {
        Conversation groupChat = new GroupChat();
        groupChat.setCourse(course);
        var message = createMessageWithReactionForUser(login, messageText, groupChat);
        addThreadReplyWithReactionForUserToPost(login, message);
        return conversationRepository.save(groupChat);
    }

    public Conversation addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(String login, Course course, String messageText) {
        Conversation oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        var message = createMessageWithReactionForUser(login, messageText, oneToOneChat);
        addThreadReplyWithReactionForUserToPost(login, message);
        return conversationRepository.save(oneToOneChat);
    }

    private void addThreadReplyWithReactionForUserToPost(String login, Post answerPostBelongsTo) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(userUtilService.getUserByLogin(login));
        answerPost.setContent("answer post");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(answerPostBelongsTo);
        addReactionForUserToAnswerPost(login, answerPost);
        postRepository.save(answerPostBelongsTo);
        answerPostRepository.save(answerPost);
    }

    private void addReactionForUserToPost(String login, Post post) {
        Reaction reaction = createReactionForUser(userUtilService.getUserByLogin(login));
        reaction.setPost(post);
        conversationRepository.save(post.getConversation());
        postRepository.save(post);
        reactionRepository.save(reaction);
    }

    private void addReactionForUserToAnswerPost(String login, AnswerPost answerPost) {
        Reaction reaction = createReactionForUser(userUtilService.getUserByLogin(login));
        reaction.setAnswerPost(answerPost);
        answerPostRepository.save(answerPost);
        reactionRepository.save(reaction);
    }

    private Reaction createReactionForUser(User user) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("heart");
        reaction.setUser(user);
        return reaction;
    }

    public Conversation addMessageInChannelOfCourseForUser(String login, Course course, String messageText) {
        Channel channel = new Channel();
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setName("channel");
        channel.setCourse(course);
        var message = createMessageWithReactionForUser(login, messageText, channel);
        addThreadReplyWithReactionForUserToPost(login, message);
        return conversationRepository.save(channel);
    }

    private Post createMessageWithReactionForUser(String login, String messageText, Conversation conversation) {
        Post message = new Post();
        message.setConversation(conversation);
        message.setAuthor(userUtilService.getUserByLogin(login));
        message.setContent(messageText);
        message.setCreationDate(ZonedDateTime.now());
        conversation.setCreator(message.getAuthor());
        addReactionForUserToPost(login, message);
        conversationRepository.save(conversation);
        message = postRepository.save(message);

        return message;
    }
}
