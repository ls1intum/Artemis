package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;
import static de.tum.cit.aet.artemis.core.service.export.DataExportUtil.createDirectoryIfNotExistent;
import static de.tum.cit.aet.artemis.core.service.export.DataExportUtil.retrieveCourseDirPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A service to create the communication data export for users.
 * <p>
 * This includes:
 * <ul>
 * <li>Messages (posts) and thread replies (answer posts)</li>
 * <li>Reactions to posts and answer posts</li>
 * <li>Saved/bookmarked posts</li>
 * <li>Notification settings</li>
 * <li>Conversation participations (channels, group chats, direct messages)</li>
 * </ul>
 * Communication data per course is stored in course-specific CSV files,
 * while saved posts, notification settings, and conversation participations are stored in root-level files.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportCommunicationDataService {

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ReactionRepository reactionRepository;

    private final SavedPostRepository savedPostRepository;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    public DataExportCommunicationDataService(PostRepository postRepository, AnswerPostRepository answerPostRepository, ReactionRepository reactionRepository,
            SavedPostRepository savedPostRepository, GlobalNotificationSettingRepository globalNotificationSettingRepository,
            ConversationParticipantRepository conversationParticipantRepository) {
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.reactionRepository = reactionRepository;
        this.savedPostRepository = savedPostRepository;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
    }

    /**
     * Creates the communication data export for a user containing all posts, answer posts, reactions,
     * saved posts, and notification settings.
     *
     * @param userId           the id of the user
     * @param workingDirectory the working directory
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createCommunicationDataExport(long userId, Path workingDirectory) throws IOException {
        var postsPerCourse = postRepository.findPostsByAuthorId(userId).stream().filter(post -> post.getCoursePostingBelongsTo() != null)
                .collect(Collectors.groupingBy(Post::getCoursePostingBelongsTo));
        // plagiarism case posts are included in the plagiarism case export
        var answerPostsPerCourse = answerPostRepository.findAnswerPostsByAuthorId(userId).stream().filter(answerPost -> answerPost.getCoursePostingBelongsTo() != null)
                .filter(answerPost -> answerPost.getPost().getPlagiarismCase() == null).collect(Collectors.groupingBy(AnswerPost::getCoursePostingBelongsTo));
        var reactions = reactionRepository.findReactionsByUserId(userId);
        var reactionsToPostsPerCourse = reactions.stream().filter(reaction -> reaction.getPost() != null).filter(reaction -> reaction.getPost().getCoursePostingBelongsTo() != null)
                .collect(Collectors.groupingBy(reaction -> reaction.getPost().getCoursePostingBelongsTo()));
        var reactionsToAnswerPostsPerCourse = reactions.stream().filter(reaction -> reaction.getAnswerPost() != null)
                .filter(reaction -> reaction.getAnswerPost().getCoursePostingBelongsTo() != null)
                .collect(Collectors.groupingBy(reaction -> reaction.getAnswerPost().getCoursePostingBelongsTo()));
        // we need to distinguish these cases because it can happen that only a specific type of communication data exists in a course
        createCommunicationDataExportIfPostsExist(workingDirectory, postsPerCourse, answerPostsPerCourse, reactionsToPostsPerCourse, reactionsToAnswerPostsPerCourse);
        createCommunicationDataExportIfAnswerPostsExist(workingDirectory, answerPostsPerCourse, reactionsToPostsPerCourse, reactionsToAnswerPostsPerCourse);
        createCommunicationDataExportIfReactionsToPostsExist(workingDirectory, reactionsToPostsPerCourse, reactionsToAnswerPostsPerCourse);
        createCommunicationDataExportIfReactionsToAnswerPostsExist(workingDirectory, reactionsToAnswerPostsPerCourse);

        // Export saved posts and notification settings (user-wide, not course-specific)
        createSavedPostsExport(userId, workingDirectory);
        createNotificationSettingsExport(userId, workingDirectory);

        // Export conversation participations (channels, group chats, direct messages)
        createConversationParticipationsExport(userId, workingDirectory);
    }

    /**
     * Creates the communication data export for a course if only reactions to answer posts exist
     *
     * @param workingDirectory                the directory where the export is stored
     * @param reactionsToAnswerPostsPerCourse the reactions to answer posts grouped by course
     */
    private void createCommunicationDataExportIfReactionsToAnswerPostsExist(Path workingDirectory, Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // it can happen that only answer post reactions exist in a course but neither posts, nor answer posts nor reactions to posts
        for (var entry : reactionsToAnswerPostsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var answerPostReactionsInCourse = entry.getValue();
            createDirectoryIfNotExistent(courseDir);
            createCommunicationDataCsvFile(courseDir, List.of(), List.of(), List.of(), answerPostReactionsInCourse);
        }
    }

    /**
     * Creates the communication data export for a course if only reactions to posts (and potentially to answer posts) exist
     *
     * @param workingDirectory                the directory where the export is stored
     * @param reactionsToPostsPerCourse       the reactions to posts grouped by course
     * @param reactionsToAnswerPostsPerCourse the reactions to answer posts grouped by course
     */
    private void createCommunicationDataExportIfReactionsToPostsExist(Path workingDirectory, Map<Course, List<Reaction>> reactionsToPostsPerCourse,
            Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // it can happen that only reactions exist in a course but no post or answer post
        for (var entry : reactionsToPostsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var postReactionsInCourse = entry.getValue();
            var answerPostReactionsInCourse = reactionsToAnswerPostsPerCourse.remove(course);
            createDirectoryIfNotExistent(courseDir);
            createCommunicationDataCsvFile(courseDir, List.of(), List.of(), postReactionsInCourse, answerPostReactionsInCourse);
        }
    }

    /**
     * Creates the communication data export for a course if only answer posts (and potentially reactions to post and answer posts) exist
     *
     * @param workingDirectory                the directory where the export is stored
     * @param answerPostsPerCourse            the answer posts grouped by course
     * @param reactionsToPostsPerCourse       the reactions to posts grouped by course
     * @param reactionsToAnswerPostsPerCourse the reactions to answer posts grouped by course
     */
    private void createCommunicationDataExportIfAnswerPostsExist(Path workingDirectory, Map<Course, List<AnswerPost>> answerPostsPerCourse,
            Map<Course, List<Reaction>> reactionsToPostsPerCourse, Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // it can happen that an answer post and reactions exist in a course but no post
        for (var entry : answerPostsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var answerPostsInCourse = entry.getValue();
            var postReactionsInCourse = reactionsToPostsPerCourse.remove(course);
            var answerPostReactionsInCourse = reactionsToAnswerPostsPerCourse.remove(course);
            createDirectoryIfNotExistent(courseDir);
            createCommunicationDataCsvFile(courseDir, List.of(), answerPostsInCourse, postReactionsInCourse, answerPostReactionsInCourse);
        }
    }

    /**
     * Creates the communication data export for a course if posts exist
     *
     * @param workingDirectory                the directory where the export is stored
     * @param postsPerCourse                  the posts grouped by course
     * @param answerPostsPerCourse            the answer posts grouped by course
     * @param reactionsToPostsPerCourse       the reactions to posts grouped by course
     * @param reactionsToAnswerPostsPerCourse the reactions to answer posts grouped by course
     */
    private void createCommunicationDataExportIfPostsExist(Path workingDirectory, Map<Course, List<Post>> postsPerCourse, Map<Course, List<AnswerPost>> answerPostsPerCourse,
            Map<Course, List<Reaction>> reactionsToPostsPerCourse, Map<Course, List<Reaction>> reactionsToAnswerPostsPerCourse) throws IOException {
        // this covers all cases where at least one post in a course exists
        for (var entry : postsPerCourse.entrySet()) {
            var course = entry.getKey();
            var courseDir = retrieveCourseDirPath(workingDirectory, course);
            var postsInCourse = entry.getValue();
            // we remove them, so we do not iterate over them again below.
            var answerPostsInCourse = answerPostsPerCourse.remove(course);
            var postReactionsInCourse = reactionsToPostsPerCourse.remove(course);
            var answerPostReactionsInCourse = reactionsToAnswerPostsPerCourse.remove(course);
            createDirectoryIfNotExistent(courseDir);
            createCommunicationDataCsvFile(courseDir, postsInCourse, answerPostsInCourse, postReactionsInCourse, answerPostReactionsInCourse);
        }
    }

    /**
     * Creates the actual CSV file containing the communication data for a course
     *
     * @param courseDir                   the directory where the CSV file is stored
     * @param postsInCourse               the posts in the course
     * @param answerPostsInCourse         the answer posts in the course
     * @param postReactionsInCourse       the reactions to posts in the course
     * @param answerPostReactionsInCourse the reactions to answer posts in the course
     */
    private void createCommunicationDataCsvFile(Path courseDir, List<Post> postsInCourse, List<AnswerPost> answerPostsInCourse, List<Reaction> postReactionsInCourse,
            List<Reaction> answerPostReactionsInCourse) throws IOException {

        // all lists apart from postsInCourse can be null, if postsInCourse is null, this method is not invoked
        if (answerPostsInCourse == null) {
            answerPostsInCourse = List.of();
        }
        if (postReactionsInCourse == null) {
            postReactionsInCourse = List.of();
        }
        if (answerPostReactionsInCourse == null) {
            answerPostReactionsInCourse = List.of();
        }
        String[] headers = { "content/emoji", "creation date", "post content reaction/reply belongs to" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).get();
        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(courseDir.resolve("messages_posts_reactions" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.println();
            printer.print("Messages/Posts");
            printer.println();
            printer.println();
            for (var post : postsInCourse) {
                printer.printRecord(post.getContent(), post.getCreationDate());
            }
            printer.println();
            printer.print("Thread replies");
            printer.println();
            printer.println();
            for (var answerPost : answerPostsInCourse) {
                printer.printRecord(answerPost.getContent(), answerPost.getCreationDate(), answerPost.getPost().getContent());
            }
            printer.println();
            printer.print("Reactions");
            printer.println();
            printer.println();
            for (var reaction : postReactionsInCourse) {
                printer.printRecord(reaction.getEmojiId(), reaction.getCreationDate(), reaction.getPost().getContent());
            }
            for (var reaction : answerPostReactionsInCourse) {
                printer.printRecord(reaction.getEmojiId(), reaction.getCreationDate(), reaction.getAnswerPost().getContent());
            }
            printer.flush();
        }
    }

    /**
     * Creates a CSV file containing the user's saved/bookmarked posts.
     *
     * @param userId           the id of the user
     * @param workingDirectory the directory where the export is stored
     * @throws IOException if an error occurs while writing the file
     */
    private void createSavedPostsExport(long userId, Path workingDirectory) throws IOException {
        List<SavedPost> savedPosts = savedPostRepository.findSavedPostsByUserId(userId);
        if (savedPosts == null || savedPosts.isEmpty()) {
            return;
        }

        String[] headers = { "post_id", "post_type", "status", "completed_at" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("saved_posts" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var savedPost : savedPosts) {
                printer.printRecord(savedPost.getPostId(), savedPost.getPostType(), savedPost.getStatus(), savedPost.getCompletedAt());
            }
            printer.flush();
        }
    }

    /**
     * Creates a CSV file containing the user's global notification settings.
     *
     * @param userId           the id of the user
     * @param workingDirectory the directory where the export is stored
     * @throws IOException if an error occurs while writing the file
     */
    private void createNotificationSettingsExport(long userId, Path workingDirectory) throws IOException {
        Set<GlobalNotificationSetting> notificationSettings = globalNotificationSettingRepository.findByUserId(userId);
        if (notificationSettings == null || notificationSettings.isEmpty()) {
            return;
        }

        String[] headers = { "notification_type", "enabled" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("notification_settings" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var setting : notificationSettings) {
                printer.printRecord(setting.getNotificationType(), setting.getEnabled());
            }
            printer.flush();
        }
    }

    /**
     * Creates a CSV file containing the user's conversation participations.
     * This includes channels, group chats, and direct messages (one-to-one chats).
     *
     * @param userId           the id of the user
     * @param workingDirectory the directory where the export is stored
     * @throws IOException if an error occurs while writing the file
     */
    private void createConversationParticipationsExport(long userId, Path workingDirectory) throws IOException {
        List<ConversationParticipant> participations = conversationParticipantRepository.findAllByUserIdWithConversationAndCourse(userId);
        if (participations == null || participations.isEmpty()) {
            return;
        }

        String[] headers = { "course_title", "conversation_type", "conversation_name", "is_moderator", "is_muted", "is_hidden", "is_favorite", "joined_at" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("conversation_participations" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var participation : participations) {
                Conversation conversation = participation.getConversation();
                String courseTitle = conversation.getCourse() != null ? conversation.getCourse().getTitle() : "";
                String conversationType = getConversationType(conversation);
                String conversationName = getConversationName(conversation);

                printer.printRecord(courseTitle, conversationType, conversationName, participation.getIsModerator(), participation.getIsMuted(), participation.getIsHidden(),
                        participation.getIsFavorite(), participation.getLastRead());
            }
            printer.flush();
        }
    }

    /**
     * Determines the type of conversation (Channel, Group Chat, or Direct Message).
     *
     * @param conversation the conversation to determine the type for
     * @return a string describing the conversation type
     */
    private String getConversationType(Conversation conversation) {
        if (conversation instanceof Channel) {
            return "Channel";
        }
        else if (conversation instanceof GroupChat) {
            return "Group Chat";
        }
        else if (conversation instanceof OneToOneChat) {
            return "Direct Message";
        }
        return "Unknown";
    }

    /**
     * Gets the name or identifier for a conversation.
     *
     * @param conversation the conversation to get the name for
     * @return the conversation name or a descriptive identifier
     */
    private String getConversationName(Conversation conversation) {
        if (conversation instanceof Channel channel) {
            return channel.getName() != null ? channel.getName() : "";
        }
        else if (conversation instanceof GroupChat groupChat) {
            return groupChat.getName() != null ? groupChat.getName() : "Group Chat #" + groupChat.getId();
        }
        else if (conversation instanceof OneToOneChat) {
            return "Direct Message #" + conversation.getId();
        }
        return "";
    }
}
