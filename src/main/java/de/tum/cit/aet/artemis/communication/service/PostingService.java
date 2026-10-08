package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.communication.web.CommunicationWebsocketTopics.COURSE_WIDE_POSTS;
import static de.tum.cit.aet.artemis.communication.web.CommunicationWebsocketTopics.PLAGIARISM_CASE_POSTS;
import static de.tum.cit.aet.artemis.communication.web.CommunicationWebsocketTopics.USER_CONVERSATION_POSTS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.dto.CommunicationCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostBroadcastDTO;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.core.dto.UserRoleDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

public abstract class PostingService {

    private static final Logger log = LoggerFactory.getLogger(PostingService.class);

    protected final CourseRepository courseRepository;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final SavedPostRepository savedPostRepository;

    protected final ConversationParticipantRepository conversationParticipantRepository;

    protected final AuthorizationCheckService authorizationCheckService;

    private final WebsocketMessagingService websocketMessagingService;

    protected static final String POST_ENTITY_NAME = "messages.post";

    /**
     * Matches the literal token "@all" if it is neither preceded by a letter, digit, underscore, "@", "/" or "=" (so not in an email address, a URL path or a URL query value)
     * nor followed by a letter, digit or underscore (so not "@alle"). The character classes are Unicode aware, the case-insensitive match only folds ASCII letters, i.e. it does
     * not depend on the default locale.
     */
    private static final Pattern AT_ALL_MENTION_PATTERN = Pattern.compile("(?<![\\p{L}\\p{N}_@/=])@all(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE);

    /** Indentation of at most this many spaces still starts a fenced code block or a blockquote line in markdown. */
    private static final int MAX_MARKDOWN_BLOCK_INDENT = 3;

    /** A tab advances to the next multiple of this many columns, so a line indented by a tab is indented by at least four columns. */
    private static final int TAB_WIDTH = 4;

    /** A fenced code block opens with at least this many backticks or tildes. */
    private static final int MIN_FENCE_LENGTH = 3;

    protected PostingService(CourseRepository courseRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService,
            ConversationParticipantRepository conversationParticipantRepository, SavedPostRepository savedPostRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.websocketMessagingService = websocketMessagingService;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.savedPostRepository = savedPostRepository;
    }

    /**
     * Helper method to prepare the post included in the websocket message and initiate the broadcasting
     *
     * @param post post that should be broadcast
     */
    public void preparePostForBroadcast(Post post) {
        try {
            var user = userRepository.getUser();
            var savedPostIds = savedPostRepository.findSavedPostIdsByUserIdAndPostType(user.getId(), PostingType.POST);
            post.setIsSaved(savedPostIds.contains(post.getId()));
            var savedAnswerIds = savedPostRepository.findSavedPostIdsByUserIdAndPostType(user.getId(), PostingType.ANSWER);
            post.getAnswers().forEach(answer -> answer.setIsSaved(savedAnswerIds.contains(answer.getId())));
        }
        catch (Exception e) {
            post.setIsSaved(false);
            post.getAnswers().forEach(answer -> answer.setIsSaved(false));
        }
    }

    /**
     * Helper method to prepare the post included in the websocket message and initiate the broadcasting
     *
     * @param updatedAnswerPost answer post that was updated
     * @param course            course the answer post belongs to
     */
    public void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course) {
        // we need to explicitly (and newly) add the updated answer post to the answers of the broadcast post to share up-to-date information
        Post updatedPost = updatedAnswerPost.getPost();
        // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
        // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
        updatedPost.removeAnswerPost(updatedAnswerPost);
        updatedPost.addAnswerPost(updatedAnswerPost);
        preparePostForBroadcast(updatedPost);
        broadcastForPost(updatedPost, CommunicationCrudAction.UPDATE, course.getId(), null);
    }

    /**
     * Broadcasts a posting related event in a course under a specific topic via websockets.
     * <p>
     * The wire payload is a {@link PostBroadcastDTO}: a cycle-free projection that drops the
     * {@code reactions → Reaction.user → User} and {@code answers → AnswerPost.post} cycles
     * before the frame leaves the server. Sending the {@link Post} entity directly used to walk
     * the same JSON cycle that fires Jackson's {@code DeserializerCache} race during integration
     * test deserialization (see {@code JacksonDeserializerInitializationConfig}).
     * <p>
     * The conversation of the post must still carry the exercise and exam of its channel: whether students may see the channel depends on them, so hide the
     * details of the conversation only after broadcasting.
     *
     * @param post       the affected post
     * @param action     the action performed on the post
     * @param courseId   the id of the course the posting belongs to
     * @param recipients the recipients for this broadcast, can be null. Note: this set is ignored when the post carries a pending Iris reply
     *                       ({@link #hasPendingIrisReply}); in that case recipients are re-resolved via {@link #getNotificationRecipients}
     *                       because per-user delivery needs each recipient's course role to choose the tutor vs. student payload.
     */
    public void broadcastForPost(Post post, CommunicationCrudAction action, Long courseId, Set<ConversationNotificationRecipientSummary> recipients) {
        // A pending (unverified) Iris reply must never reach students. Clients replace their whole cached
        // post — including its answers — on every UPDATE frame, so a single shared payload cannot serve
        // students and tutors at once: re-broadcasting an unrelated change (a reaction, an edit, another
        // reply) would otherwise push the pending reply out on the course-wide topic students subscribe to.
        // When the post carries a pending Iris reply we therefore deliver per-user instead.
        Conversation postConversation = post.getConversation();
        if (postConversation != null && hasPendingIrisReply(post)) {
            broadcastPostWithPendingIrisReply(post, action, postConversation);
            return;
        }

        // Build the cycle-free wire payload before adjusting entity state — PostResponseDTO.from
        // walks the entity exactly once.
        PostBroadcastDTO broadcastPayload = PostBroadcastDTO.from(post, action);

        if (postConversation != null) {
            if (postConversation instanceof Channel channel && channel.getIsCourseWide()) {
                if (channel.isVisibleToStudents()) {
                    websocketMessagingService.sendMessage(COURSE_WIDE_POSTS.at(courseId), broadcastPayload);
                }
                else {
                    // Staff discuss an exercise or exam in its channel before students can see it. The course-wide topic reaches every
                    // student of the course, so the post goes to the personal topic of each staff member instead.
                    userRepository.findStaffNotificationRecipientsInCourseForConversation(channel.getId(), courseId)
                            .forEach(recipient -> websocketMessagingService.sendMessage(USER_CONVERSATION_POSTS.at(recipient.userId()), broadcastPayload));
                }
            }
            else {
                if (recipients == null) {
                    // send to all participants of the conversation
                    recipients = getConversationParticipantsAsSummaries(postConversation);
                }
                recipients.forEach(recipient -> websocketMessagingService.sendMessage(USER_CONVERSATION_POSTS.at(recipient.userId()), broadcastPayload));
            }
        }
        else if (post.getPlagiarismCase() != null) {
            websocketMessagingService.sendMessage(PLAGIARISM_CASE_POSTS.at(post.getPlagiarismCase().getId()), broadcastPayload);
        }
    }

    /**
     * @return {@code true} if the post carries at least one unverified Iris reply that students must not see
     */
    private boolean hasPendingIrisReply(Post post) {
        return post.getAnswers() != null && post.getAnswers().stream().anyMatch(AnswerPost::isUnverifiedIrisReply);
    }

    /**
     * Central visibility rule for pending (unverified) Iris replies: students must never see them. Strips every
     * unverified Iris reply from the in-memory answers of the given posts unless the requesting user is at least a
     * tutor in the course. Callers must apply this before projecting posts to a REST/websocket response on any
     * lookup path that a student can reach (paginated messages, source posts, saved posts, forwarded messages, ...).
     *
     * @param posts    the posts whose answers are filtered in place
     * @param courseId the course used for the tutor role check
     */
    public void hidePendingIrisRepliesFromStudents(Collection<Post> posts, Long courseId) {
        if (authorizationCheckService.isAtLeastTeachingAssistantInCourse(courseId)) {
            return;
        }
        posts.forEach(post -> {
            if (post.getAnswers() != null) {
                post.getAnswers().removeIf(AnswerPost::isUnverifiedIrisReply);
            }
        });
    }

    /**
     * Broadcasts a post that carries at least one unverified Iris reply. The full post (pending reply
     * included) goes to tutors so the review controls stay live, while everyone else receives a copy
     * with the pending Iris replies stripped. Both payloads are addressed to each recipient's personal
     * topic — never the shared course-wide topic — because students subscribe to it as well and a client
     * replaces its whole cached post on every UPDATE, which would otherwise expose the pending reply.
     *
     * @param post         the post to broadcast; its answers are mutated in place to build the student payload
     * @param action       the CRUD action this broadcast describes
     * @param conversation the conversation the post belongs to
     */
    private void broadcastPostWithPendingIrisReply(Post post, CommunicationCrudAction action, Conversation conversation) {
        // Tutor payload first, while the pending reply is still attached.
        PostBroadcastDTO tutorPayload = PostBroadcastDTO.from(post, action);
        // Then strip the pending replies and re-project for everyone else.
        post.getAnswers().removeIf(AnswerPost::isUnverifiedIrisReply);
        PostBroadcastDTO studentPayload = PostBroadcastDTO.from(post, action);

        // Students must not see posts of a channel whose exercise or exam is not visible to them yet.
        boolean visibleToStudents = !(conversation instanceof Channel channel) || channel.isVisibleToStudents();
        // Resolve recipients together with their course role — a caller-supplied set need not carry the tutor flag.
        getNotificationRecipients(conversation).filter(recipient -> visibleToStudents || recipient.isAtLeastTutorInCourse()).forEach(recipient -> {
            PostBroadcastDTO payload = recipient.isAtLeastTutorInCourse() ? tutorPayload : studentPayload;
            websocketMessagingService.sendMessage(USER_CONVERSATION_POSTS.at(recipient.userId()), payload);
        });
    }

    /**
     * Gets the participants of a conversation and maps them to ConversationNotificationRecipientSummary records
     *
     * @param postConversation the conversation
     * @return Set of ConversationNotificationRecipientSummary
     */
    private Set<ConversationNotificationRecipientSummary> getConversationParticipantsAsSummaries(Conversation postConversation) {
        return conversationParticipantRepository.findConversationParticipantsByConversationId(postConversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsMuted(),
                        participant.getIsHidden() != null && participant.getIsHidden(), false))
                .collect(Collectors.toSet());
    }

    /**
     * Determines the participants of a conversation that should receive the new message.
     *
     * @param conversation conversation the participants are supposed be retrieved
     * @return users that should receive the new message
     */
    protected Stream<ConversationNotificationRecipientSummary> getNotificationRecipients(Conversation conversation) {
        if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
            Course course = conversation.getCourse();
            return userRepository.findAllNotificationRecipientsInCourseForConversation(conversation.getId(), course.getId()).stream();
        }

        return conversationParticipantRepository.findConversationParticipantsWithUserCourseRolesByConversationId(conversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsMuted(),
                        participant.getIsHidden() != null && participant.getIsHidden(),
                        authorizationCheckService.isAtLeastTeachingAssistantInCourse(conversation.getCourse(), participant.getUser())));
    }

    protected Course preCheckUserAndCourseForMessaging(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new BadRequestAlertException("Communication and messaging is disabled for this course", getEntityName(), "400", true);
        }
        return course;
    }

    /**
     * Ensures that user is allowed to communicate or message in the given course
     *
     * @param user   that wants to communicate or message
     * @param course the course in which the user wants to communicate or message
     */
    public void preCheckUserAndCourseForCommunicationOrMessaging(User user, Course course) {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new BadRequestAlertException("Communication and messaging is disabled for this course", getEntityName(), "400", true);
        }
    }

    /**
     * Ensures that the given conversation belongs to the course identified by the (authoritative) path {@code courseId}.
     * <p>
     * The course context comes from the URL path and is used for authorization, while the target conversation is resolved
     * from the request body / loaded entity. If the two diverge, a user authorized for one course could read or write in a
     * conversation of another course (cross-course injection / broken access control). Reject such inconsistent requests.
     *
     * @param conversation the conversation the posting targets
     * @param courseId     the id of the course taken from the API path
     * @throws BadRequestAlertException if the conversation does not belong to the given course
     */
    protected void ensureConversationBelongsToCourseElseThrow(Conversation conversation, Long courseId) {
        // Conversation#course is eagerly fetched, so this is a no-DB-cost id comparison
        if (!Objects.equals(conversation.getCourse().getId(), courseId)) {
            throw new BadRequestAlertException("The conversation does not belong to the specified course", getEntityName(), "conversationCourseMismatch");
        }
    }

    /**
     * Sets the author role of each post and its answers in a given list of posts.
     *
     * <p>
     * This method processes a list of posts to determine the roles of their authors within the context of a specified course.
     * It collects a unique set of user IDs from the posts and their answers, fetches the corresponding user roles from the repository,
     * and sets the author roles accordingly.
     * </p>
     *
     * @param posts    the list of posts for which the author roles need to be set
     * @param courseId the ID of the course in which the posts exist
     */
    protected void setAuthorRoleOfPostings(Collection<Post> posts, Long courseId) {
        // prepares a unique set of userIds that authored the current list of postings
        Set<Long> userIds = new HashSet<>();
        posts.forEach(post -> {
            // needs to handle posts created by SingleUserNotificationService.notifyUserAboutNewPlagiarismCaseBySystem
            if (post.getAuthor() != null) {
                userIds.add(post.getAuthor().getId());
            }
            post.getAnswers().forEach(answerPost -> userIds.add(answerPost.getAuthor().getId()));
        });

        // we only fetch the minimal data needed for the mapping to avoid performance issues
        Set<UserRoleDTO> userRoles = userRepository.findUserRolesInCourse(userIds, courseId);
        log.debug("userRepository.findUserRolesInCourse done for {} authors ", userRoles.size());

        Map<Long, UserRoleDTO> authorRoles = userRoles.stream().collect(Collectors.toMap(UserRoleDTO::userId, Function.identity()));

        // sets respective author role to display user authority icon on posting headers
        posts.stream().filter(post -> post.getAuthor() != null).forEach(post -> {
            post.setAuthorRole(authorRoles.get(post.getAuthor().getId()).role());
            post.getAnswers().forEach(answerPost -> answerPost.setAuthorRole(authorRoles.get(answerPost.getAuthor().getId()).role()));
        });
    }

    /**
     * Assigns the author role to a given posting based on the user's groups and authorities within a course.
     *
     * <p>
     * This helper method determines the appropriate author role (INSTRUCTOR, TUTOR, or USER) for the author of a posting
     * based on their permissions and groups within the specified course. The author must be fetched with their authorities
     * and groups set prior to calling this method.
     * </p>
     *
     * <p>
     * Note: The course to which the posting belongs must be explicitly fetched and provided to handle cases of new post creation.
     * </p>
     *
     * @param posting       the posting to assign an author role to
     * @param postingCourse the course that the posting belongs to, required to determine the author's role
     */
    protected void setAuthorRoleForPosting(Posting posting, Course postingCourse) {
        if (authorizationCheckService.isAtLeastInstructorInCourse(postingCourse, posting.getAuthor())) {
            posting.setAuthorRole(UserRole.INSTRUCTOR);
        }
        else if (authorizationCheckService.isTeachingAssistantInCourse(postingCourse, posting.getAuthor())
                || authorizationCheckService.isEditorInCourse(postingCourse, posting.getAuthor())) {
            posting.setAuthorRole(UserRole.TUTOR);
        }
        else {
            posting.setAuthorRole(UserRole.USER);
        }
    }

    protected abstract String getEntityName();

    /**
     * Checks whether a posting contains the "@all" token, which pings every member of a group chat. Tokens in a blockquote, a fenced code block or an inline code span do not
     * count, so quoting a message that contains "@all" or explaining the feature in code does not ping the group again. Neither does the token in a URL.
     *
     * @param postingContent content of the posting, may be null
     * @return true if the content contains the "@all" token outside of quotes and code
     */
    public static boolean containsAtAllMention(String postingContent) {
        if (postingContent == null) {
            return false;
        }
        return AT_ALL_MENTION_PATTERN.matcher(withoutQuotesAndCode(postingContent)).find();
    }

    /**
     * Removes blockquote lines, fenced code blocks (including an unterminated one that runs to the end, as in markdown), indented code blocks and inline code spans from the
     * content. The content is user provided, so this is a single linear pass without backtracking regular expressions.
     * <p>
     * Consecutive lines of a paragraph are collected and processed together, because an inline code span may continue over line endings within a paragraph. A blank line, a
     * fence and a blockquote line end the paragraph. A line indented by four or more columns that does not continue a paragraph belongs to an indented code block.
     */
    private static String withoutQuotesAndCode(String content) {
        StringBuilder result = new StringBuilder(content.length());
        StringBuilder paragraph = new StringBuilder();
        char openFence = 0;
        int openFenceLength = 0;
        int lineStart = 0;
        while (lineStart <= content.length()) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            lineStart = lineEnd + 1;

            int indent = 0;
            int column = 0;
            while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
                column = line.charAt(indent) == '\t' ? (column / TAB_WIDTH + 1) * TAB_WIDTH : column + 1;
                indent++;
            }
            char first = indent < line.length() ? line.charAt(indent) : 0;
            boolean mayStartBlock = column <= MAX_MARKDOWN_BLOCK_INDENT;

            if (openFenceLength > 0) {
                if (mayStartBlock && first == openFence && isClosingFence(line, indent, openFenceLength)) {
                    openFenceLength = 0;
                }
            }
            else if (line.isBlank()) {
                appendWithoutInlineCode(result, paragraph);
            }
            else if (mayStartBlock && (first == '`' || first == '~') && opensFence(line, indent)) {
                appendWithoutInlineCode(result, paragraph);
                openFence = first;
                openFenceLength = lengthOfRun(line, indent);
            }
            else if (mayStartBlock && first == '>') {
                appendWithoutInlineCode(result, paragraph);
            }
            else if (mayStartBlock || !paragraph.isEmpty()) {
                // an indented line directly after a paragraph line continues the paragraph
                if (!paragraph.isEmpty()) {
                    paragraph.append('\n');
                }
                paragraph.append(line);
            }
            // else: the line belongs to an indented code block
        }
        appendWithoutInlineCode(result, paragraph);
        return result.toString();
    }

    /** Appends the collected paragraph without its inline code spans, followed by a line break, and empties the paragraph. Does nothing for an empty paragraph. */
    private static void appendWithoutInlineCode(StringBuilder result, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            result.append(withoutInlineCode(paragraph.toString())).append('\n');
            paragraph.setLength(0);
        }
    }

    private static int lengthOfRun(String line, int start) {
        int end = start;
        while (end < line.length() && line.charAt(end) == line.charAt(start)) {
            end++;
        }
        return end - start;
    }

    /** A run of at least three backticks or tildes opens a block, unless a backtick run is followed by another backtick, which makes the line an inline code span. */
    private static boolean opensFence(String line, int indent) {
        int length = lengthOfRun(line, indent);
        return length >= MIN_FENCE_LENGTH && !(line.charAt(indent) == '`' && line.indexOf('`', indent + length) >= 0);
    }

    /** A closing fence consists of the fence character at least as often as the opening one and nothing but white space after it. */
    private static boolean isClosingFence(String line, int indent, int openFenceLength) {
        int length = lengthOfRun(line, indent);
        return length >= openFenceLength && line.substring(indent + length).isBlank();
    }

    /**
     * Removes inline code spans of a paragraph, which may consist of several lines. A span starts with a run of backticks and ends with the next run of exactly the same length,
     * an unmatched run is plain text. The next run of the same length is determined once per run, so the effort is linear in the length of the paragraph.
     */
    private static String withoutInlineCode(String text) {
        if (text.indexOf('`') < 0) {
            return text;
        }
        List<int[]> runs = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '`') {
                int length = lengthOfRun(text, i);
                runs.add(new int[] { i, length });
                i += length;
            }
            else {
                i++;
            }
        }
        int[] nextRunOfSameLength = new int[runs.size()];
        Map<Integer, Integer> lastSeenByLength = new HashMap<>();
        for (int run = runs.size() - 1; run >= 0; run--) {
            nextRunOfSameLength[run] = lastSeenByLength.getOrDefault(runs.get(run)[1], -1);
            lastSeenByLength.put(runs.get(run)[1], run);
        }

        StringBuilder result = new StringBuilder(text.length());
        int copiedUntil = 0;
        int run = 0;
        while (run < runs.size()) {
            int closingRun = nextRunOfSameLength[run];
            if (closingRun < 0) {
                run++;
                continue;
            }
            result.append(text, copiedUntil, runs.get(run)[0]).append(' ');
            copiedUntil = runs.get(closingRun)[0] + runs.get(closingRun)[1];
            run = closingRun + 1;
        }
        return result.append(text, copiedUntil, text.length()).toString();
    }

    /**
     * Checks whether a posting pings all members of its conversation. The "@all" token only counts in group chats, in every other conversation it is plain text.
     *
     * @param conversation   the conversation the posting belongs to
     * @param postingContent content of the posting, may be null
     * @return true if the posting contains the "@all" token and the conversation is a group chat
     */
    protected static boolean mentionsAllMembers(Conversation conversation, String postingContent) {
        return conversation instanceof GroupChat && containsAtAllMention(postingContent);
    }

    /**
     * Determines the users that receive the mention notification: the explicitly mentioned users and, if the posting pings all members, every member who did not mute or hide
     * the conversation. A user is contained once even if the posting mentions them by name and through "@all". The author never receives a mention notification.
     * Explicitly mentioned users are kept as they are, i.e. they are notified even if they muted the conversation.
     *
     * @param mentionedUserRecipients the explicitly mentioned users who are allowed to receive the notification
     * @param mentionsAllMembers      whether the posting pings all members of the conversation
     * @param conversationRecipients  the members of the conversation including their mute and hide flags
     * @param authorId                the id of the author of the posting
     * @return the users that receive the mention notification
     */
    protected static List<User> resolveMentionRecipients(List<User> mentionedUserRecipients, boolean mentionsAllMembers,
            Collection<ConversationNotificationRecipientSummary> conversationRecipients, Long authorId) {
        if (!mentionsAllMembers) {
            return mentionedUserRecipients;
        }
        Map<Long, User> recipientsById = new LinkedHashMap<>();
        mentionedUserRecipients.forEach(user -> recipientsById.put(user.getId(), user));
        conversationRecipients.stream().filter(summary -> summary.shouldNotifyRecipient() && !Objects.equals(summary.userId(), authorId))
                .forEach(summary -> recipientsById.computeIfAbsent(summary.userId(),
                        userId -> new User(userId, summary.userLogin(), summary.firstName(), summary.lastName(), summary.userLangKey(), summary.userEmail())));
        return new ArrayList<>(recipientsById.values());
    }

    /**
     * Gets the list of logins for users mentioned in a posting.
     * Throws an exception, if a mentioned user is not part of the course.
     *
     * @param course         course of the posting
     * @param postingContent content of the posting
     * @return set of mentioned users
     */
    protected Set<User> parseUserMentions(@NonNull Course course, String postingContent) {
        // Define a regular expression to match text enclosed in [user]...[/user] tags, along with login inside parentheses () within those tags.
        // It makes use of the possessive quantifier "*+" to avoid backtracking and increase performance.
        // Explanation:
        // - "\\[user\\]" matches the literal string "[user]".
        // - "([^\\[\\]()]*+)" captures any characters that are not '[', ']', '(', or ')' zero or more times. This captures the full name the user mention.
        // - "\\(?" matches the literal '(' character.
        // - "([^\\[\\]()]*+)" captures any characters that are not '[', ']', '(', or ')' zero or more times. This captures the content within parentheses.
        // - "\\)?" matches the literal ')' character.
        // - "\\[/user\\]" matches the literal string "[/user]".
        String regex = "\\[user\\]([^\\[\\]()]*+)\\(?([^\\[\\]()]*+)\\)?\\[/user\\]";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(Optional.ofNullable(postingContent).orElse(""));

        Map<String, String> matches = new HashMap<>();

        // Find and save all matches in the list
        while (matcher.find()) {
            String fullName = matcher.group(1);
            String userLogin = matcher.group(2);

            matches.put(userLogin, fullName);
        }

        // Pre-load course roles for all mentioned users so the per-user isAtLeastStudentInCourse check below - and the
        // isAtLeastTeachingAssistantInCourse check that SingleUserNotificationService later runs on this same set of
        // users - resolve in memory instead of one EXISTS query per mentioned user.
        Set<User> mentionedUsers = userRepository.findAllWithCourseRolesAndAuthoritiesByDeletedIsFalseAndLoginIn(matches.keySet());

        if (mentionedUsers.size() != matches.size()) {
            throw new BadRequestAlertException("At least one of the mentioned users does not exist", POST_ENTITY_NAME, "invalidUserMention");
        }

        mentionedUsers.forEach(user -> {
            if (!user.getName().equals(matches.get(user.getLogin()))) {
                throw new BadRequestAlertException("The name provided for user " + user.getLogin() + " does not match the user's full name " + user.getName(), POST_ENTITY_NAME,
                        "invalidUserMention");
            }

            if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
                throw new BadRequestAlertException("The user " + user.getLogin() + " is not a member of the course", POST_ENTITY_NAME, "invalidUserMention");
            }
        });

        return mentionedUsers;
    }
}
