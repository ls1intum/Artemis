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
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
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

        Course course1 = courseUtilService.createCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        course1.addExercises(textExercise);
        textExercise = exerciseRepo.save(textExercise);

        Lecture lecture = LectureFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        course1.addLectures(lecture);
        lecture = lectureRepo.save(lecture);

        courseRepo.save(course1);

        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(textExercise);
        plagiarismCase.setStudent(userUtilService.getUserByLogin(userPrefix + "student1"));
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        List<Post> posts = new ArrayList<>();

        // add posts to exercise
        posts.addAll(createBasicPosts(textExercise, userPrefix));

        // add posts to lecture
        posts.addAll(createBasicPosts(lecture, userPrefix));

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

    private List<Post> createBasicPosts(Exercise exerciseContext, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "student");
            postToAdd.setExercise(exerciseContext);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private List<Post> createBasicPosts(Lecture lectureContext, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "tutor");
            postToAdd.setLecture(lectureContext);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
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
}
