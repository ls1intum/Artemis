import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text/text-block.model';

export enum TextAssessmentEventType {
    ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK = 'ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK',
    ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK = 'ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK',
    DELETE_FEEDBACK = 'DELETE_FEEDBACK',
    EDIT_AUTOMATIC_FEEDBACK = 'EDIT_AUTOMATIC_FEEDBACK',
    SUBMIT_ASSESSMENT = 'SUBMIT_ASSESSMENT',
    ASSESS_NEXT_SUBMISSION = 'ASSESS_NEXT_SUBMISSION',
}

export class TextAssessmentEvent implements BaseEntity {
    public id?: number;
    public userId?: number;
    public timestamp?: dayjs.Dayjs;
    public eventType?: TextAssessmentEventType;
    public feedbackType?: FeedbackType;
    public segmentType?: TextBlockType;
    public courseId?: number;
    public textExerciseId?: number;
    public participationId?: number;
    public submissionId?: number;

    constructor(userId?: number, courseId?: number, textExerciseId?: number, participationId?: number, submissionId?: number) {
        this.userId = userId;
        this.courseId = courseId;
        this.textExerciseId = textExerciseId;
        this.participationId = participationId;
        this.submissionId = submissionId;
    }

    setEventType(type: TextAssessmentEventType) {
        this.eventType = type;
        return this;
    }

    setFeedbackType(type?: FeedbackType) {
        this.feedbackType = type;
    }

    setSegmentType(type?: TextBlockType) {
        this.segmentType = type;
    }
}
