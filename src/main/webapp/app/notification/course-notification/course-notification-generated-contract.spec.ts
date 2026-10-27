import { describe, expect, expectTypeOf, it } from 'vitest';
import { CourseNotification as WireNotification } from 'app/openapi/model/course-notification';
import { CourseNotificationPage } from 'app/openapi/model/course-notification-page';
import { AddedToChannelPayload } from 'app/openapi/model/added-to-channel-payload';
import { AtlasCompetencyUpdatePayload } from 'app/openapi/model/atlas-competency-update-payload';
import { AttachmentChangedPayload } from 'app/openapi/model/attachment-changed-payload';
import { ChannelDeletedPayload } from 'app/openapi/model/channel-deleted-payload';
import { DeregisteredFromTutorialGroupPayload } from 'app/openapi/model/deregistered-from-tutorial-group-payload';
import { DuplicateTestCasePayload } from 'app/openapi/model/duplicate-test-case-payload';
import { ExerciseAssessedPayload } from 'app/openapi/model/exercise-assessed-payload';
import { ExerciseOpenForPracticePayload } from 'app/openapi/model/exercise-open-for-practice-payload';
import { ExerciseUpdatedPayload } from 'app/openapi/model/exercise-updated-payload';
import { IrisResponseNeedsReviewPayload } from 'app/openapi/model/iris-response-needs-review-payload';
import { IrisResponsePayload } from 'app/openapi/model/iris-response-payload';
import { NewAnnouncementPayload } from 'app/openapi/model/new-announcement-payload';
import { NewAnswerPayload } from 'app/openapi/model/new-answer-payload';
import { NewCpcPlagiarismCasePayload } from 'app/openapi/model/new-cpc-plagiarism-case-payload';
import { NewExercisePayload } from 'app/openapi/model/new-exercise-payload';
import { NewManualFeedbackRequestPayload } from 'app/openapi/model/new-manual-feedback-request-payload';
import { NewMentionPayload } from 'app/openapi/model/new-mention-payload';
import { NewPlagiarismCasePayload } from 'app/openapi/model/new-plagiarism-case-payload';
import { NewPostPayload } from 'app/openapi/model/new-post-payload';
import { PlagiarismCaseVerdictPayload } from 'app/openapi/model/plagiarism-case-verdict-payload';
import { ProgrammingBuildRunUpdatePayload } from 'app/openapi/model/programming-build-run-update-payload';
import { ProgrammingTestCasesChangedPayload } from 'app/openapi/model/programming-test-cases-changed-payload';
import { QuizExerciseStartedPayload } from 'app/openapi/model/quiz-exercise-started-payload';
import { RegisteredToTutorialGroupPayload } from 'app/openapi/model/registered-to-tutorial-group-payload';
import { RemovedFromChannelPayload } from 'app/openapi/model/removed-from-channel-payload';
import { TutorialGroupAssignedPayload } from 'app/openapi/model/tutorial-group-assigned-payload';
import { TutorialGroupDeletedPayload } from 'app/openapi/model/tutorial-group-deleted-payload';
import { TutorialGroupUnassignedPayload } from 'app/openapi/model/tutorial-group-unassigned-payload';

function checkBranch(notification: WireNotification) {
    switch (notification.notificationType) {
        case 'addedToChannelNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<AddedToChannelPayload | undefined>();
            return;
        case 'atlasCompetencyUpdateNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<AtlasCompetencyUpdatePayload | undefined>();
            return;
        case 'attachmentChangedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<AttachmentChangedPayload | undefined>();
            return;
        case 'channelDeletedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<ChannelDeletedPayload | undefined>();
            return;
        case 'deregisteredFromTutorialGroupNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<DeregisteredFromTutorialGroupPayload | undefined>();
            return;
        case 'duplicateTestCaseNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<DuplicateTestCasePayload | undefined>();
            return;
        case 'exerciseAssessedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<ExerciseAssessedPayload | undefined>();
            return;
        case 'exerciseOpenForPracticeNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<ExerciseOpenForPracticePayload | undefined>();
            return;
        case 'exerciseUpdatedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<ExerciseUpdatedPayload | undefined>();
            return;
        case 'irisResponseNeedsReviewNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<IrisResponseNeedsReviewPayload | undefined>();
            return;
        case 'irisResponseNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<IrisResponsePayload | undefined>();
            return;
        case 'newAnnouncementNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewAnnouncementPayload | undefined>();
            return;
        case 'newAnswerNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewAnswerPayload | undefined>();
            return;
        case 'newCpcPlagiarismCaseNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewCpcPlagiarismCasePayload | undefined>();
            return;
        case 'newExerciseNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewExercisePayload | undefined>();
            return;
        case 'newManualFeedbackRequestNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewManualFeedbackRequestPayload | undefined>();
            return;
        case 'newMentionNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewMentionPayload | undefined>();
            return;
        case 'newPlagiarismCaseNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewPlagiarismCasePayload | undefined>();
            return;
        case 'newPostNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<NewPostPayload | undefined>();
            return;
        case 'plagiarismCaseVerdictNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<PlagiarismCaseVerdictPayload | undefined>();
            return;
        case 'programmingBuildRunUpdateNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<ProgrammingBuildRunUpdatePayload | undefined>();
            return;
        case 'programmingTestCasesChangedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<ProgrammingTestCasesChangedPayload | undefined>();
            return;
        case 'quizExerciseStartedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<QuizExerciseStartedPayload | undefined>();
            return;
        case 'registeredToTutorialGroupNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<RegisteredToTutorialGroupPayload | undefined>();
            return;
        case 'removedFromChannelNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<RemovedFromChannelPayload | undefined>();
            return;
        case 'tutorialGroupAssignedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<TutorialGroupAssignedPayload | undefined>();
            return;
        case 'tutorialGroupDeletedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<TutorialGroupDeletedPayload | undefined>();
            return;
        case 'tutorialGroupUnassignedNotification':
            expectTypeOf(notification.payload).toEqualTypeOf<TutorialGroupUnassignedPayload | undefined>();
            return;
        default:
            expectTypeOf(notification).toEqualTypeOf<never>();
    }
}

describe('generated course notification type contracts', () => {
    it('narrows all 28 complete envelopes without a cast or a nested discriminator', () => {
        const notifications: WireNotification[] = [
            { notificationType: 'addedToChannelNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'atlasCompetencyUpdateNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'attachmentChangedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'channelDeletedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'deregisteredFromTutorialGroupNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'duplicateTestCaseNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'exerciseAssessedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'exerciseOpenForPracticeNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'exerciseUpdatedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'irisResponseNeedsReviewNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'irisResponseNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newAnnouncementNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newAnswerNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newCpcPlagiarismCaseNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newExerciseNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newManualFeedbackRequestNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newMentionNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newPlagiarismCaseNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'newPostNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'plagiarismCaseVerdictNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'programmingBuildRunUpdateNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'programmingTestCasesChangedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'quizExerciseStartedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'registeredToTutorialGroupNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'removedFromChannelNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'tutorialGroupAssignedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'tutorialGroupDeletedNotification', notificationId: 0, courseId: 0 },
            { notificationType: 'tutorialGroupUnassignedNotification', notificationId: 0, courseId: 0 },
        ];
        notifications.forEach(checkBranch);
        expect(notifications).toHaveLength(28);
        expectTypeOf<keyof WireNotification>().toEqualTypeOf<
            | 'notificationType'
            | 'notificationId'
            | 'courseId'
            | 'creationDate'
            | 'category'
            | 'courseTitle'
            | 'courseIconUrl'
            | 'payload'
            | 'status'
            | 'relativeWebAppUrl'
            | 'parameters'
        >();
    });

    it('retains nullable page items and honest legacy scalar values', () => {
        expectTypeOf<NonNullable<CourseNotificationPage['content']>[number]>().toEqualTypeOf<WireNotification | null>();
        expectTypeOf<NonNullable<WireNotification['parameters']>[string]>().toEqualTypeOf<string | number | boolean | null>();
        const page: CourseNotificationPage = { content: [null], pageNumber: 0, pageSize: 10, totalElements: 1, totalPages: 1 };
        expect(page.content?.[0]).toBeNull();
    });
});
