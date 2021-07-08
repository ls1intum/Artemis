import { Injectable } from '@angular/core';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextAssessmentEvent, TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { lastValueFrom } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class TextAssessmentAnalytics {
    private userId: number;

    constructor(protected assessmentsService: TextAssessmentService, protected accountService: AccountService) {
        this.userId = accountService.userIdentity ? accountService.userIdentity.id! : 0;
    }

    sendAssessmentEvent() {
        const assessmentEventToSend: TextAssessmentEvent = {
            userId: this.userId,
            eventType: TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN,
            feedbackType: FeedbackType.AUTOMATIC,
            segmentType: TextBlockType.AUTOMATIC,
            courseId: 2,
            textExerciseId: 3,
            submissionId: 4,
        };
        lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    }

    async sendAssessmentEventDelete() {
        const assessmentEventToSend: TextAssessmentEvent = {
            userId: this.userId,
            eventType: TextAssessmentEventType.DELETE_AUTOMATIC_FEEDBACK,
            feedbackType: FeedbackType.AUTOMATIC,
            segmentType: TextBlockType.AUTOMATIC,
            courseId: 2,
            textExerciseId: 3,
            submissionId: 4,
        };
        await lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    }

    async sendAssessmentEventEditFeedback() {
        const assessmentEventToSend: TextAssessmentEvent = {
            userId: this.userId,
            eventType: TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK,
            feedbackType: FeedbackType.AUTOMATIC,
            segmentType: TextBlockType.AUTOMATIC,
            courseId: 2,
            textExerciseId: 3,
            submissionId: 4,
        };
        await lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    }

    sendAssessmentEventOnHoverWarning() {
        const assessmentEventToSend: TextAssessmentEvent = {
            userId: this.userId,
            eventType: TextAssessmentEventType.HOVER_OVER_IMPACT_WARNING,
            feedbackType: FeedbackType.AUTOMATIC,
            segmentType: TextBlockType.AUTOMATIC,
            courseId: 2,
            textExerciseId: 3,
            submissionId: 4,
        };
        lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    }

    sendAssessmentEventOnConflictClicked() {
        const assessmentEventToSend: TextAssessmentEvent = {
            userId: this.userId,
            eventType: TextAssessmentEventType.CLICK_TO_RESOLVE_CONFLICT,
            feedbackType: FeedbackType.AUTOMATIC,
            segmentType: TextBlockType.AUTOMATIC,
            courseId: 2,
            textExerciseId: 3,
            submissionId: 4,
        };
        lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    }
}
