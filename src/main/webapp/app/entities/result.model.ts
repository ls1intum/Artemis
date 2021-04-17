import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';
import { Participation } from 'app/entities/participation/participation.model';
import { Submission } from 'app/entities/submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

export class Result implements BaseEntity {
    public id?: number;
    public resultString?: string;
    public completionDate?: Moment;
    public successful?: boolean;
    public hasFeedback?: boolean;
    public score?: number;
    public assessmentType?: AssessmentType;
    public rated?: boolean;
    public hasComplaint?: boolean;
    public exampleResult?: boolean;

    public submission?: Submission;
    public assessor?: User;
    public feedbacks?: Feedback[];
    public participation?: Participation;

    // helper attributes
    public durationInMinutes?: number;

    constructor() {
        this.successful = false; // default value
    }

    /**
     * Checks whether the result is a manual result. A manual result can be from type MANUAL or SEMI_AUTOMATIC
     *
     * @return true if the result is a manual result
     */
    public static isManualResult(that: Result): boolean {
        return that.assessmentType === AssessmentType.MANUAL || that.assessmentType === AssessmentType.SEMI_AUTOMATIC;
    }
}

/**
 * Sets the transient property copiedFeedback for feedbacks when comparing a submissions results of two correction rounds
 * copiedFeedback indicates if the feedback is directly copied and unmodified compared to the first correction round
 *
 * @param correctionRound current correction round
 * @param submission current submission
 */
export function handleFeedbackCorrectionRoundTag(correctionRound: number, submission: Submission) {
    if (correctionRound > 0 && submission?.results && submission.results.length > 1) {
        const firstResult = submission!.results![0] as Result;
        const secondCorrectionFeedback1 = submission!.results![1].feedbacks as Feedback[];
        secondCorrectionFeedback1!.forEach((secondFeedback) => {
            firstResult.feedbacks!.forEach((firstFeedback) => {
                if (
                    secondFeedback.copiedFeedbackId === undefined &&
                    secondFeedback.type === firstFeedback.type &&
                    secondFeedback.credits === firstFeedback.credits &&
                    secondFeedback.detailText === firstFeedback.detailText &&
                    secondFeedback.reference === firstFeedback.reference &&
                    secondFeedback.text === firstFeedback.text
                ) {
                    secondFeedback.copiedFeedbackId = firstFeedback.id;
                } else if (
                    secondFeedback.copiedFeedbackId === firstFeedback.id &&
                    !(
                        secondFeedback.type === firstFeedback.type &&
                        secondFeedback.credits === firstFeedback.credits &&
                        secondFeedback.detailText === firstFeedback.detailText &&
                        secondFeedback.reference === firstFeedback.reference &&
                        secondFeedback.text === firstFeedback.text
                    )
                ) {
                    secondFeedback.copiedFeedbackId = undefined;
                }
            });
        });
    }
}
