package de.tum.in.www1.artemis.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import jakarta.validation.constraints.NotNull;

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
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
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

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    /**
     * Creates and saves a Course with disabled posts.
     *
     * @return The created Course
     */
    public Course createCourseWithPostsDisabled() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        return courseRepo.save(course);
    }

    /**
     * Creates and saves a Course. It also creates and saves two TextExercises and two Lectures for the Course. It also creates and saves a PlagiarismCase for the first
     * TextExercise.
     * Creates and saves Posts for each of the created entities.
     *
     * @param userPrefix The prefix of the author's login
     * @return A List of the created Posts
     */
    public List<Post> createPostsWithinCourse(String userPrefix) {

        List<Channel> testExerciseChannels = new ArrayList<>();
        List<Channel> testLectureChannels = new ArrayList<>();

        Course course1 = courseUtilService.createCourse();
        for (int i = 0; i < 2; i++) {
            TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
            course1.addExercises(textExercise);
            textExercise = exerciseRepo.save(textExercise);
            Channel exerciseChannel = exerciseUtilService.addChannelToExercise(textExercise);
            testExerciseChannels.add(exerciseChannel);

            Lecture lecture = LectureFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
            course1.addLectures(lecture);
            lecture = lectureRepo.save(lecture);
            Channel lectureChannel = lectureUtilService.addLectureChannel(lecture);
            testLectureChannels.add(lectureChannel);
        }

        courseRepo.save(course1);

        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(testExerciseChannels.get(0).getExercise());
        plagiarismCase.setStudent(userUtilService.getUserByLogin(userPrefix + "student1"));
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        List<Post> posts = new ArrayList<>();

        // add posts to exercise
        posts.addAll(createBasicPosts(testExerciseChannels.toArray(Channel[]::new), userPrefix));

        // add posts to lecture
        posts.addAll(createBasicPosts(testLectureChannels.toArray(Channel[]::new), userPrefix));

        // add post to plagiarismCase
        posts.add(createBasicPost(plagiarismCase, userPrefix));

        posts.addAll(createBasicPosts(createOneToOneChat(course1, userPrefix), userPrefix, "tutor"));
        posts.addAll(createBasicPosts(createCourseWideChannel(course1, userPrefix), userPrefix, "student"));
        posts.addAll(createBasicPosts(createCourseWideChannel(course1, userPrefix), userPrefix, "student"));

        return posts;
    }

    /**
     * Creates and saves a OneToOneChat for the given Course. It also creates and saves two ConversationParticipants for the OneToOneChat. It also creates and saves the given
     * number
     * of Posts with AnswerPosts and Reactions.
     *
     * @param course        The Course the OneToOneChat belongs to
     * @param student1      The first User to create a ConversationParticipant for
     * @param student2      The second User to create a ConversationParticipant for
     * @param numberOfPosts The number of Posts to create
     * @param userPrefix    The prefix of the author's login (the login is appended with "student1")
     * @return A List of the created Posts
     */
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
        chat = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(chat.getId()).orElseThrow();

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

    /**
     * Creates and saves a Course. It also creates and saves two TextExercises and two Lectures. It also creates and saves a PlagiarismCase for the first TextExercise.
     * Creates and saves a Post with an AnswerPost for each of the created entities.
     *
     * @param userPrefix The prefix of the author's login (the login is appended with "student1")
     * @return A List of the created Posts
     */
    public List<Post> createPostsWithAnswerPostsWithinCourse(String userPrefix) {
        List<Post> posts = createPostsWithinCourse(userPrefix);

        // add answer for one post in each context (lecture, exercise, course-wide, conversation)
        Post lecturePost = posts.stream().filter(coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getLecture() != null).findFirst().orElseThrow();
        lecturePost.setAnswers(createBasicAnswers(lecturePost, userPrefix));
        lecturePost.getAnswers().addAll(createBasicAnswers(lecturePost, userPrefix));
        postRepository.save(lecturePost);

        Post exercisePost = posts.stream().filter(coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getExercise() != null).findFirst().orElseThrow();
        exercisePost.setAnswers(createBasicAnswers(exercisePost, userPrefix));
        postRepository.save(exercisePost);

        // resolved post
        Post courseWidePost = posts.stream().filter(
                coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getIsCourseWide() && channel.getExercise() == null && channel.getLecture() == null)
                .findFirst().orElseThrow();
        courseWidePost.setAnswers(createBasicAnswersThatResolves(courseWidePost, userPrefix));
        postRepository.save(courseWidePost);

        Post conversationPost = posts.stream().filter(coursePost -> coursePost.getConversation() instanceof OneToOneChat).findFirst().orElseThrow();
        conversationPost.setAnswers(createBasicAnswers(conversationPost, userPrefix));
        postRepository.save(conversationPost);

        Post studentConversationPost = posts.stream().filter(coursePost -> coursePost.getConversation() != null && coursePost.getAuthor().getLogin().contains("student"))
                .findFirst().orElseThrow();
        studentConversationPost.setAnswers(createBasicAnswers(studentConversationPost, userPrefix));
        postRepository.save(studentConversationPost);

        Post plagiarismPost = posts.stream().filter(coursePost -> coursePost.getPlagiarismCase() != null).findFirst().orElseThrow();
        plagiarismPost.setAnswers(createBasicAnswers(plagiarismPost, userPrefix));
        plagiarismPost.getAnswers().addAll(createBasicAnswers(plagiarismPost, userPrefix));
        postRepository.save(plagiarismPost);

        return posts;
    }

    /**
     * Creates and saves a post for each of the given channelContexts.
     *
     * @param channelContexts The channels the posts belong to
     * @param userPrefix      The prefix of the authors' logins (the logins are appended with ["student" + (index of channelContext + 1)])
     * @return A List of the created Posts
     */
    private List<Post> createBasicPosts(Channel[] channelContexts, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (Channel channelContext : channelContexts) {
            for (int i = 0; i < 4; i++) {
                Post postToAdd = ConversationFactory.createBasicPost(i, userUtilService.getUserByLoginWithoutAuthorities(String.format("%s%s", userPrefix + "student", (i + 1))));
                postToAdd.setConversation(channelContext);
                postRepository.save(postToAdd);
                posts.add(postToAdd);
            }
        }

        return posts;
    }

    /**
     * Creates and saves a Post for the given PlagiarismCase.
     *
     * @param plagiarismCase The PlagiarismCase the Post belongs to
     * @param userPrefix     The prefix of the author's login the Post belongs to (the login is appended with "instructor1")
     * @return The created Post
     */
    public Post createBasicPost(PlagiarismCase plagiarismCase, String userPrefix) {
        Post postToAdd = ConversationFactory.createBasicPost(0, userUtilService.getUserByLoginWithoutAuthorities(String.format("%s%s", userPrefix + "instructor", 1)));
        postToAdd.setPlagiarismCase(plagiarismCase);
        return postRepository.save(postToAdd);
    }

    /**
     * Creates and saves 3 Posts. These Posts are part of the given Conversation and each Post has a different author.
     *
     * @param conversation The Conversation the Posts belong to
     * @param userPrefix   The prefix of the authors' logins (the logins are appended with userRole + (index of created Post + 1))
     * @param userRole     The role of the Users the Posts belong to
     * @return A List of the created Posts
     */
    private List<Post> createBasicPosts(Conversation conversation, String userPrefix, String userRole) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Post postToAdd = ConversationFactory.createBasicPost(i, userUtilService.getUserByLoginWithoutAuthorities(String.format("%s%s", userPrefix + userRole, (i + 1))));
            postToAdd.setConversation(conversation);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    /**
     * Creates and saves an AnswerPost for the given Post.
     *
     * @param post       The Post the AnswerPost belongs to
     * @param userPrefix The prefix of the author's login (the login is appended with "student1")
     * @return A Set of the created AnswerPost
     */
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

    /**
     * Creates and saves an AnswerPost for the given Post. The AnswerPost is marked as resolving the Post and the Post is marked as resolved.
     *
     * @param post       The Post the AnswerPost resolves
     * @param userPrefix The prefix of the author's login (the login is appended with "student1")
     * @return A Set of the created AnswerPost
     */
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

    /**
     * Asserts that the sensitive information of the Users is hidden in the given Postings.
     *
     * @param postings The list of Postings to check
     */
    public <T extends Posting> void assertSensitiveInformationHidden(@NotNull Collection<T> postings) {
        for (Posting posting : postings) {
            assertSensitiveInformationHidden(posting);
        }
    }

    /**
     * Asserts that the sensitive information of the User is hidden in a Posting.
     *
     * @param posting The Posting to check
     */
    public void assertSensitiveInformationHidden(@NotNull Posting posting) {
        if (posting.getAuthor() != null) {
            assertThat(posting.getAuthor().getEmail()).isNull();
            assertThat(posting.getAuthor().getLogin()).isNull();
            assertThat(posting.getAuthor().getRegistrationNumber()).isNull();
        }
    }

    /**
     * Asserts that the sensitive information of the User is hidden in a Reaction.
     *
     * @param reaction The Reaction to check
     */
    public void assertSensitiveInformationHidden(@NotNull Reaction reaction) {
        if (reaction.getUser() != null) {
            assertThat(reaction.getUser().getEmail()).isNull();
            assertThat(reaction.getUser().getLogin()).isNull();
            assertThat(reaction.getUser().getRegistrationNumber()).isNull();
        }
    }

    /**
     * Creates and saves a OneToOneChat for the given Course. It also creates and saves two ConversationParticipants for the OneToOneChat.
     *
     * @param course     The Course the OneToOneChat belongs to
     * @param userPrefix The prefix of the Users' logins (the logins are appended with "tutor1" and "tutor2")
     * @return The created OneToOneChat
     */
    public Conversation createOneToOneChat(Course course, String userPrefix) {
        Conversation conversation = new OneToOneChat();
        conversation.setCourse(course);
        conversation = conversationRepository.save(conversation);

        List<ConversationParticipant> conversationParticipants = new ArrayList<>();
        conversationParticipants.add(createConversationParticipant(conversation, userPrefix + "tutor1", false));
        conversationParticipants.add(createConversationParticipant(conversation, userPrefix + "tutor2", false));

        conversation.setConversationParticipants(new HashSet<>(conversationParticipants));
        return conversationRepository.save(conversation);
    }

    /**
     * Creates and saves a Channel for the given Course. The Channel is course wide.
     *
     * @param course      The Course the Channel belongs to
     * @param channelName The name of the Channel
     * @return The created Channel
     */
    public Channel createCourseWideChannel(Course course, String channelName) {
        Channel channel = ConversationFactory.generatePublicChannel(course, channelName, true);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves a Channel for the given Course. The Channel is course wide.
     *
     * @param course         The Course the Channel belongs to
     * @param channelName    The name of the Channel
     * @param isAnnouncement True if the Channel is an announcement channel
     * @return The created Channel
     */
    public Channel createCourseWideChannel(Course course, String channelName, boolean isAnnouncement) {
        Channel channel = ConversationFactory.generatePublicChannel(course, channelName, true, isAnnouncement);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves a Channel for the given Course. The Channel is not course wide.
     *
     * @param course      The Course the Channel belongs to
     * @param channelName The name of the Channel
     * @return The created Channel
     */
    public Channel createPublicChannel(Course course, String channelName) {
        Channel channel = ConversationFactory.generatePublicChannel(course, channelName, false);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves an announcement Channel for the given Course. The Channel is not course wide.
     *
     * @param course      The Course the Channel belongs to
     * @param channelName The name of the Channel
     * @return The created Channel
     */
    public Channel createAnnouncementChannel(Course course, String channelName) {
        Channel channel = ConversationFactory.generateAnnouncementChannel(course, channelName, false);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves a ConversationParticipant for the given Conversation.
     *
     * @param conversation The Conversation the ConversationParticipant belongs to
     * @param userName     The login of the User the ConversationParticipant belongs to
     * @return The created ConversationParticipant
     */
    public ConversationParticipant addParticipantToConversation(Conversation conversation, String userName) {
        return createConversationParticipant(conversation, userName, false);
    }

    /**
     * Creates and saves a ConversationParticipant for the given Conversation.
     *
     * @param conversation         The Conversation the ConversationParticipant belongs to
     * @param userName             The login of the User the ConversationParticipant belongs to
     * @param isConversationHidden True if the Conversation is hidden
     * @return The created ConversationParticipant
     */
    public ConversationParticipant addParticipantToConversation(Conversation conversation, String userName, boolean isConversationHidden) {
        return createConversationParticipant(conversation, userName, isConversationHidden);
    }

    /**
     * Creates and saves a ConversationParticipant for the given Conversation.
     *
     * @param conversation         The Conversation the ConversationParticipant belongs to
     * @param userName             The login of the User the ConversationParticipant belongs to
     * @param isConversationHidden True if the Conversation is hidden
     * @return The created ConversationParticipant
     */
    private ConversationParticipant createConversationParticipant(Conversation conversation, String userName, boolean isConversationHidden) {
        ConversationParticipant conversationParticipant = new ConversationParticipant();
        conversationParticipant.setConversation(conversation);
        conversationParticipant.setLastRead(conversation.getLastMessageDate());
        conversationParticipant.setUser(userUtilService.getUserByLogin(userName));
        conversationParticipant.setIsHidden(isConversationHidden);

        return conversationParticipantRepository.save(conversationParticipant);
    }

    /**
     * Creates and saves a GroupChat. It also creates and saves a Post with a Reaction and an AnswerPost with a Reaction.
     *
     * @param login       The login of the User the Post and AnswerPost belong to
     * @param course      The Course the GroupChat belongs to
     * @param messageText The content of the Post
     * @return The created GroupChat as a Conversation
     */
    public Conversation addMessageWithReplyAndReactionInGroupChatOfCourseForUser(String login, Course course, String messageText) {
        Conversation groupChat = new GroupChat();
        groupChat.setCourse(course);
        var message = createMessageWithReactionForUser(login, messageText, groupChat);
        addThreadReplyWithReactionForUserToPost(login, message);
        return conversationRepository.save(groupChat);
    }

    /**
     * Creates and saves a Post with a Reaction.
     *
     * @param login        The login of the User the Post and Reaction belong to
     * @param conversation The Conversation the Post belongs to
     * @return The created Post
     */
    public Post addMessageToConversation(String login, Conversation conversation) {
        return createMessageWithReactionForUser(login, "test", conversation);
    }

    /**
     * Creates and saves a OneToOneChat. It also creates and saves a Post with a Reaction and an AnswerPost with a Reaction.
     *
     * @param login       The login of the User the Post and AnswerPost belong to
     * @param course      The Course the OneToOneChat belongs to
     * @param messageText The content of the Post
     * @return The created OneToOneChat as a Conversation
     */
    public Conversation addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(String login, Course course, String messageText) {
        Conversation oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        var message = createMessageWithReactionForUser(login, messageText, oneToOneChat);
        addThreadReplyWithReactionForUserToPost(login, message);
        return conversationRepository.save(oneToOneChat);
    }

    /**
     * Creates and saves an AnswerPost with a Reaction for the given Post.
     *
     * @param login               The login of the User the AnswerPost and Reaction belong to
     * @param answerPostBelongsTo The Post the AnswerPost belongs to
     */
    public void addThreadReplyWithReactionForUserToPost(String login, Post answerPostBelongsTo) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(userUtilService.getUserByLogin(login));
        answerPost.setContent("answer post");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(answerPostBelongsTo);
        addReactionForUserToAnswerPost(login, answerPost);
        postRepository.save(answerPostBelongsTo);
        answerPostRepository.save(answerPost);
    }

    /**
     * Creates and saves a Reaction for the given Post.
     *
     * @param login The login of the User the Reaction belongs to
     * @param post  The Post the Reaction belongs to
     */
    public void addReactionForUserToPost(String login, Post post) {
        Reaction reaction = ConversationFactory.createReactionForUser(userUtilService.getUserByLogin(login));
        reaction.setPost(post);
        conversationRepository.save(post.getConversation());
        postRepository.save(post);
        reactionRepository.save(reaction);
    }

    /**
     * Creates and saves a Reaction for the given AnswerPost.
     *
     * @param login      The login of the User the Reaction belongs to
     * @param answerPost The AnswerPost the Reaction belongs to
     */
    public void addReactionForUserToAnswerPost(String login, AnswerPost answerPost) {
        Reaction reaction = ConversationFactory.createReactionForUser(userUtilService.getUserByLogin(login));
        reaction.setAnswerPost(answerPost);
        answerPostRepository.save(answerPost);
        reactionRepository.save(reaction);
    }

    /**
     * Creates and saves a Channel, a Post and a Reaction. It also creates and saves an AnswerPost with a Reaction.
     *
     * @param login       The login of the User the Post, AnswerPost and Reaction belong to
     * @param course      The Course the Channel belongs to
     * @param messageText The content of the Post
     * @return The created Channel as a Conversation
     */
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

    /**
     * Creates and saves a Channel, a Post and a Reaction.
     *
     * @param login       The login of the User the Post and Reaction belong to
     * @param course      The Course the Channel belongs to
     * @param messageText The content of the Post
     * @return The created Channel as a Conversation
     */
    public Conversation addOneMessageForUserInCourse(String login, Course course, String messageText) {
        Post message = new Post();
        Channel channel = new Channel();
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setName("channel");
        channel.setCourse(course);
        message.setConversation(channel);
        message.setAuthor(userUtilService.getUserByLogin(login));
        message.setContent(messageText);
        message.setCreationDate(ZonedDateTime.now());
        channel.setCreator(message.getAuthor());
        addReactionForUserToPost(login, message);
        conversationRepository.save(channel);
        postRepository.save(message);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves a Post with a Reaction.
     *
     * @param login        The login of the User the Post and Reaction belong to
     * @param messageText  The content of the Post
     * @param conversation The Conversation the Post belongs to
     * @return The created Post
     */
    private Post createMessageWithReactionForUser(String login, String messageText, Conversation conversation) {
        Post message = new Post();
        message.setConversation(conversation);
        message.setAuthor(userUtilService.getUserByLogin(login));
        message.setContent(messageText);
        message.setCreationDate(ZonedDateTime.now());
        conversation.setCreator(message.getAuthor());
        addReactionForUserToPost(login, message);
        conversationRepository.save(conversation);

        return postRepository.save(message);
    }
}
