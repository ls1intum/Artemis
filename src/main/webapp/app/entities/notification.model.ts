import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export enum NotificationType {
    SYSTEM = 'system',
    CONNECTION = 'connection',
    GROUP = 'group',
    SINGLE = 'single',
    CONVERSATION = 'conversation',
}

export class Notification implements BaseEntity {
    public id?: number;
    public notificationType?: NotificationType;
    public title?: string;
    public text?: string;
    public textIsPlaceholder?: boolean;
    public placeholderValues?: string;
    public notificationDate?: dayjs.Dayjs;
    public target?: string;
    public author?: User;

    protected constructor(notificationType: NotificationType) {
        this.notificationType = notificationType;
    }
}

/**
 * Corresponds to the server-side NotificationConstants(.java) constant Strings
 */
export const EXERCISE_SUBMISSION_ASSESSED_TITLE = 'artemisApp.singleUserNotification.title.exerciseSubmissionAssessed';

export const ATTACHMENT_CHANGE_TITLE = 'artemisApp.groupNotification.title.attachmentChange';

export const EXERCISE_RELEASED_TITLE = 'artemisApp.groupNotification.title.exerciseReleased';

export const EXERCISE_PRACTICE_TITLE = 'artemisApp.groupNotification.title.exercisePractice';

export const QUIZ_EXERCISE_STARTED_TITLE = 'artemisApp.groupNotification.title.quizExerciseStarted';

export const EXERCISE_UPDATED_TITLE = 'artemisApp.groupNotification.title.exerciseUpdated';

export const DUPLICATE_TEST_CASE_TITLE = 'artemisApp.groupNotification.title.duplicateTestCase';

export const ILLEGAL_SUBMISSION_TITLE = 'artemisApp.groupNotification.title.illegalSubmission';

export const NEW_EXERCISE_POST_TITLE = 'artemisApp.groupNotification.title.newExercisePost';

export const NEW_LECTURE_POST_TITLE = 'artemisApp.groupNotification.title.newLecturePost';

export const NEW_ANNOUNCEMENT_POST_TITLE = 'artemisApp.groupNotification.title.newAnnouncementPost';

export const NEW_EXAM_POST_TITLE = 'artemisApp.groupNotification.title.newExamPost';

export const NEW_COURSE_POST_TITLE = 'artemisApp.groupNotification.title.newCoursePost';

export const NEW_REPLY_FOR_EXERCISE_POST_TITLE = 'artemisApp.groupNotification.title.newReplyForExercisePost';

export const NEW_REPLY_FOR_LECTURE_POST_TITLE = 'artemisApp.groupNotification.title.newReplyForLecturePost';

export const NEW_REPLY_FOR_COURSE_POST_TITLE = 'artemisApp.groupNotification.title.newReplyForCoursePost';

export const NEW_REPLY_FOR_EXAM_POST_TITLE = 'artemisApp.groupNotification.title.newReplyForExamPost';

export const FILE_SUBMISSION_SUCCESSFUL_TITLE = 'artemisApp.singleUserNotification.title.fileSubmissionSuccessful';

export const COURSE_ARCHIVE_STARTED_TITLE = 'artemisApp.groupNotification.title.courseArchiveStarted';

export const COURSE_ARCHIVE_FINISHED_TITLE = 'artemisApp.groupNotification.title.courseArchiveFinished';

export const COURSE_ARCHIVE_FAILED_TITLE = 'artemisApp.groupNotification.title.courseArchiveFailed';

export const EXAM_ARCHIVE_STARTED_TITLE = 'artemisApp.groupNotification.title.examArchiveStarted';

export const EXAM_ARCHIVE_FINISHED_TITLE = 'artemisApp.groupNotification.title.examArchiveFinished';

export const EXAM_ARCHIVE_FAILED_TITLE = 'artemisApp.groupNotification.title.examArchiveFailed';

export const PROGRAMMING_TEST_CASES_CHANGED_TITLE = 'artemisApp.groupNotification.title.programmingTestCasesChanged';

export const NEW_MANUAL_FEEDBACK_REQUEST_TITLE = 'artemisApp.groupNotification.title.newManualFeedbackRequest';

export const NEW_PLAGIARISM_CASE_STUDENT_TITLE = 'artemisApp.singleUserNotification.title.newPlagiarismCaseStudent';

export const PLAGIARISM_CASE_VERDICT_STUDENT_TITLE = 'artemisApp.singleUserNotification.title.plagiarismCaseVerdictStudent';

export const PLAGIARISM_CASE_REPLY_TITLE = 'artemisApp.singleUserNotification.title.plagiarismCaseReply';

export const TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupRegistrationStudent';

export const TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupDeregistrationStudent';

export const TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupRegistrationTutor';

export const TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupDeregistrationTutor';

export const TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupRegistrationMultipleTutor';

export const TUTORIAL_GROUP_DELETED_TITLE = 'artemisApp.tutorialGroupNotification.title.tutorialGroupDeleted';

export const TUTORIAL_GROUP_UPDATED_TITLE = 'artemisApp.tutorialGroupNotification.title.tutorialGroupUpdated';

export const TUTORIAL_GROUP_ASSIGNED_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupAssigned';

export const TUTORIAL_GROUP_UNASSIGNED_TITLE = 'artemisApp.singleUserNotification.title.tutorialGroupUnassigned';

export const NEW_MESSAGE_TITLE = 'artemisApp.conversationNotification.title.newMessage';

export const NEW_REPLY_MESSAGE_TITLE = 'artemisApp.singleUserNotification.title.messageReply';

export const MENTIONED_IN_MESSAGE_TITLE = 'artemisApp.singleUserNotification.title.mentionedInMessage';

export const CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE = 'artemisApp.singleUserNotification.title.createOneToOneChat';

export const CONVERSATION_CREATE_GROUP_CHAT_TITLE = 'artemisApp.singleUserNotification.title.createGroupChat';

export const CONVERSATION_ADD_USER_CHANNEL_TITLE = 'artemisApp.singleUserNotification.title.addUserChannel';

export const CONVERSATION_ADD_USER_GROUP_CHAT_TITLE = 'artemisApp.singleUserNotification.title.addUserGroupChat';

export const CONVERSATION_DELETE_CHANNEL_TITLE = 'artemisApp.singleUserNotification.title.deleteChannel';

export const CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE = 'artemisApp.singleUserNotification.title.removeUserGroupChat';

export const CONVERSATION_REMOVE_USER_CHANNEL_TITLE = 'artemisApp.singleUserNotification.title.removeUserChannel';

// edge case: has no separate notificationType. Is created based on EXERCISE_UPDATED for exam exercises
export const LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE = 'artemisApp.groupNotification.title.liveExamExerciseUpdate';

export const QUIZ_EXERCISE_STARTED_TEXT = 'artemisApp.groupNotification.text.quizExerciseStarted';

export const DATA_EXPORT_CREATED_TITLE = 'artemisApp.singleUserNotification.title.dataExportCreated';
export const DATA_EXPORT_FAILED_TITLE = 'artemisApp.singleUserNotification.title.dataExportFailed';

export const NEW_MESSAGE_CHANNEL_TEXT = 'artemisApp.conversationNotification.text.newMessageChannel';

export const NEW_MESSAGE_GROUP_CHAT_TEXT = 'artemisApp.conversationNotification.text.newMessageGroupChat';

export const NEW_MESSAGE_DIRECT_TEXT = 'artemisApp.conversationNotification.text.newMessageDirect';

export const MESSAGE_REPLY_IN_CONVERSATION_TEXT = 'artemisApp.singleUserNotification.text.messageReply';

export const MESSAGE_REPLY_IN_CHANNEL_TEXT = 'artemisApp.singleUserNotification.text.channelMessageReply';
