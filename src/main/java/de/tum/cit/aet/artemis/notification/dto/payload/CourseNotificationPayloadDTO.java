package de.tum.cit.aet.artemis.notification.dto.payload;

import java.io.Serializable;

/**
 * The payload of one notification type: the values a client or an email template renders, and nothing else.
 * <p>
 * One record per notification type, so that the shape a notification puts on the wire and into the distributed store
 * is declared rather than reflected. The record is the notification's state, so its fields are declared once, and
 * {@code DistributedDataSurfaceTest} records the payload types instead of an opaque {@code Object}.
 * <p>
 * Values are persisted as {@code course_notification_parameter} rows, one per component, and read back by Jackson,
 * which coerces the stored strings into the component types. Keep the components to scalars and strings for that
 * reason: a nested object has no meaningful string form.
 * <p>
 * Sealed on purpose. The set of payloads is the set of notification types, so a new one is a deliberate addition here
 * rather than something that appears in the store unannounced, and {@code DistributedDataSurfaceTest} can enumerate
 * what a cached notification may hold by following the permitted types.
 */
public sealed interface CourseNotificationPayloadDTO extends Serializable
        permits AddedToChannelPayloadDTO, AttachmentChangedPayloadDTO, ChannelDeletedPayloadDTO, DeregisteredFromTutorialGroupPayloadDTO, DuplicateTestCasePayloadDTO,
        ExerciseAssessedPayloadDTO, ExerciseOpenForPracticePayloadDTO, ExerciseUpdatedPayloadDTO, IrisResponseNeedsReviewPayloadDTO, NewAnnouncementPayloadDTO, NewAnswerPayloadDTO,
        NewCpcPlagiarismCasePayloadDTO, NewExercisePayloadDTO, NewManualFeedbackRequestPayloadDTO, NewMentionPayloadDTO, NewPlagiarismCasePayloadDTO, NewPostPayloadDTO,
        PlagiarismCaseVerdictPayloadDTO, ProgrammingBuildRunUpdatePayloadDTO, ProgrammingTestCasesChangedPayloadDTO, QuizExerciseStartedPayloadDTO,
        RegisteredToTutorialGroupPayloadDTO, RemovedFromChannelPayloadDTO, TutorialGroupAssignedPayloadDTO, TutorialGroupDeletedPayloadDTO, TutorialGroupUnassignedPayloadDTO {
}
