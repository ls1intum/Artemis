/**
 * The values each notification type carries, mirroring the payload records on the server.
 *
 * A notification's type decides which payload it holds, so a reader narrows on `notificationType` with
 * {@link payloadOf} and then reads real properties, rather than indexing a map of unknown values by string.
 *
 * Keep this in step with `de.tum.cit.aet.artemis.notification.dto.payload`: the Java interface is sealed over exactly
 * these payloads, and `DistributedDataSurfaceTest` records their shapes, so a change there is a reviewed change.
 */

/** The values a addedToChannelNotification carries. */
export interface AddedToChannelPayload {
    channelModerator?: string;
    channelName?: string;
    channelId?: number;
}

/** The values a attachmentChangedNotification carries. */
export interface AttachmentChangedPayload {
    attachmentName?: string;
    unitName?: string;
    exerciseId?: number;
    lectureId?: number;
}

/** The values a channelDeletedNotification carries. */
export interface ChannelDeletedPayload {
    deletingUser?: string;
    channelName?: string;
}

/** The values a deregisteredFromTutorialGroupNotification carries. */
export interface DeregisteredFromTutorialGroupPayload {
    groupTitle?: string;
    groupId?: number;
    moderatorName?: string;
}

/** The values a duplicateTestCaseNotification carries. */
export interface DuplicateTestCasePayload {
    exerciseId?: number;
    exerciseTitle?: string;
    releaseDate?: string;
    dueDate?: string;
    examId?: number;
    exerciseGroupId?: number;
}

/** The values a exerciseAssessedNotification carries. */
export interface ExerciseAssessedPayload {
    exerciseId?: number;
    exerciseTitle?: string;
    exerciseType?: string;
    numberOfPoints?: number;
    score?: number;
    examId?: number;
}

/** The values a exerciseOpenForPracticeNotification carries. */
export interface ExerciseOpenForPracticePayload {
    exerciseId?: number;
    exerciseTitle?: string;
}

/** The values a exerciseUpdatedNotification carries. */
export interface ExerciseUpdatedPayload {
    exerciseId?: number;
    exerciseTitle?: string;
    examId?: number;
    exerciseGroupId?: number;
    exerciseType?: string;
}

/** The values a irisResponseNeedsReviewNotification carries. */
export interface IrisResponseNeedsReviewPayload {
    postMarkdownContent?: string;
    postCreationDate?: string;
    postAuthorName?: string;
    postId?: number;
    replyMarkdownContent?: string;
    replyCreationDate?: string;
    replyId?: number;
    replyConfidence?: number;
    channelName?: string;
    channelId?: number;
}

/** The values a newAnnouncementNotification carries. */
export interface NewAnnouncementPayload {
    postId?: number;
    postTitle?: string;
    postMarkdownContent?: string;
    authorName?: string;
    authorImageUrl?: string;
    authorId?: number;
    channelId?: number;
}

/** The values a newAnswerNotification carries. */
export interface NewAnswerPayload {
    postMarkdownContent?: string;
    postCreationDate?: string;
    postAuthorName?: string;
    postId?: number;
    replyMarkdownContent?: string;
    replyCreationDate?: string;
    replyAuthorName?: string;
    replyAuthorId?: number;
    replyImageUrl?: string;
    replyId?: number;
    channelName?: string;
    channelId?: number;
    replyIsBot?: boolean;
}

/** The values a newCpcPlagiarismCaseNotification carries. */
export interface NewCpcPlagiarismCasePayload {
    exerciseId?: number;
    exerciseTitle?: string;
    exerciseType?: string;
    postMarkdownContent?: string;
    examId?: number;
}

/** The values a newExerciseNotification carries. */
export interface NewExercisePayload {
    exerciseId?: number;
    exerciseTitle?: string;
    difficulty?: string;
    releaseDate?: string;
    dueDate?: string;
    numberOfPoints?: number;
}

/** The values a newManualFeedbackRequestNotification carries. */
export interface NewManualFeedbackRequestPayload {
    exerciseId?: number;
    exerciseTitle?: string;
    examId?: number;
}

/** The values a newMentionNotification carries. */
export interface NewMentionPayload {
    postMarkdownContent?: string;
    postCreationDate?: string;
    postAuthorName?: string;
    postId?: number;
    replyMarkdownContent?: string;
    replyCreationDate?: string;
    replyAuthorName?: string;
    replyAuthorId?: number;
    replyImageUrl?: string;
    replyId?: number;
    channelName?: string;
    channelId?: number;
    replyIsBot?: boolean;
}

/** The values a newPlagiarismCaseNotification carries. */
export interface NewPlagiarismCasePayload {
    exerciseId?: number;
    exerciseTitle?: string;
    exerciseType?: string;
    postMarkdownContent?: string;
    examId?: number;
}

/** The values a newPostNotification carries. */
export interface NewPostPayload {
    postId?: number;
    postMarkdownContent?: string;
    channelId?: number;
    channelName?: string;
    channelType?: string;
    authorName?: string;
    authorImageUrl?: string;
    authorId?: number;
    authorIsBot?: boolean;
}

/** The values a plagiarismCaseVerdictNotification carries. */
export interface PlagiarismCaseVerdictPayload {
    exerciseId?: number;
    exerciseTitle?: string;
    exerciseType?: string;
    verdict?: string;
    examId?: number;
}

/** The values a programmingBuildRunUpdateNotification carries. */
export interface ProgrammingBuildRunUpdatePayload {
    exerciseId?: number;
    exerciseTitle?: string;
    examId?: number;
    exerciseGroupId?: number;
}

/** The values a programmingTestCasesChangedNotification carries. */
export interface ProgrammingTestCasesChangedPayload {
    exerciseId?: number;
    exerciseTitle?: string;
    examId?: number;
    exerciseGroupId?: number;
}

/** The values a quizExerciseStartedNotification carries. */
export interface QuizExerciseStartedPayload {
    exerciseId?: number;
    exerciseTitle?: string;
}

/** The values a registeredToTutorialGroupNotification carries. */
export interface RegisteredToTutorialGroupPayload {
    groupTitle?: string;
    groupId?: number;
    moderatorName?: string;
}

/** The values a removedFromChannelNotification carries. */
export interface RemovedFromChannelPayload {
    channelModerator?: string;
    channelName?: string;
    channelId?: number;
}

/** The values a tutorialGroupAssignedNotification carries. */
export interface TutorialGroupAssignedPayload {
    groupTitle?: string;
    groupId?: number;
    moderatorName?: string;
}

/** The values a tutorialGroupDeletedNotification carries. */
export interface TutorialGroupDeletedPayload {
    groupTitle?: string;
    groupId?: number;
    moderatorName?: string;
}

/** The values a tutorialGroupUnassignedNotification carries. */
export interface TutorialGroupUnassignedPayload {
    groupTitle?: string;
    groupId?: number;
    moderatorName?: string;
}

/** Maps a notification type to the payload it carries. */
export interface CourseNotificationPayloadByType {
    addedToChannelNotification: AddedToChannelPayload;
    attachmentChangedNotification: AttachmentChangedPayload;
    channelDeletedNotification: ChannelDeletedPayload;
    deregisteredFromTutorialGroupNotification: DeregisteredFromTutorialGroupPayload;
    duplicateTestCaseNotification: DuplicateTestCasePayload;
    exerciseAssessedNotification: ExerciseAssessedPayload;
    exerciseOpenForPracticeNotification: ExerciseOpenForPracticePayload;
    exerciseUpdatedNotification: ExerciseUpdatedPayload;
    irisResponseNeedsReviewNotification: IrisResponseNeedsReviewPayload;
    newAnnouncementNotification: NewAnnouncementPayload;
    newAnswerNotification: NewAnswerPayload;
    newCpcPlagiarismCaseNotification: NewCpcPlagiarismCasePayload;
    newExerciseNotification: NewExercisePayload;
    newManualFeedbackRequestNotification: NewManualFeedbackRequestPayload;
    newMentionNotification: NewMentionPayload;
    newPlagiarismCaseNotification: NewPlagiarismCasePayload;
    newPostNotification: NewPostPayload;
    plagiarismCaseVerdictNotification: PlagiarismCaseVerdictPayload;
    programmingBuildRunUpdateNotification: ProgrammingBuildRunUpdatePayload;
    programmingTestCasesChangedNotification: ProgrammingTestCasesChangedPayload;
    quizExerciseStartedNotification: QuizExerciseStartedPayload;
    registeredToTutorialGroupNotification: RegisteredToTutorialGroupPayload;
    removedFromChannelNotification: RemovedFromChannelPayload;
    tutorialGroupAssignedNotification: TutorialGroupAssignedPayload;
    tutorialGroupDeletedNotification: TutorialGroupDeletedPayload;
    tutorialGroupUnassignedNotification: TutorialGroupUnassignedPayload;
}

/** Any notification payload. */
export type CourseNotificationPayload = CourseNotificationPayloadByType[keyof CourseNotificationPayloadByType];
