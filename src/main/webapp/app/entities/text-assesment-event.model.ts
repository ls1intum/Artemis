import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { FeedbackType } from 'app/entities/feedback.model';

export enum TextAssessmentEventType {
    ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK = 'ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK',
    ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK = 'ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK',
    CLICK_TO_RESOLVE_CONFLICT = 'CLICK_TO_RESOLVE_CONFLICT',
    HOVER_OVER_IMPACT_WARNING = 'HOVER_OVER_IMPACT_WARNING',
    VIEW_AUTOMATIC_SUGGESTION_ORIGIN = 'VIEW_AUTOMATIC_SUGGESTION_ORIGIN',
    DELETE_AUTOMATIC_FEEDBACK = 'DELETE_AUTOMATIC_FEEDBACK',
    EDIT_AUTOMATIC_FEEDBACK = 'EDIT_AUTOMATIC_FEEDBACK',
}

export class TextAssessmentEvent implements BaseEntity {
    public id?: number;
    public userId?: number;
    public timestamp?: Moment;
    public eventType?: TextAssessmentEventType;
    public feedbackType?: FeedbackType;
    public segmentType?: TextBlockType;
    public courseId?: number;
    public textExerciseId?: number;
    public submissionId?: number;

    protected constructor() {}
}
