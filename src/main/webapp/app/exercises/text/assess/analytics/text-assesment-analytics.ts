import { Injectable } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextAssessmentEvent, TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { lastValueFrom } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';

@Injectable({ providedIn: 'root' })
export class TextAssessmentAnalytics {
    private userId: number;
    private courseId: number;
    private textExerciseId: number;
    private participationId: number;
    private submissionId: number;
    private eventToSend: TextAssessmentEvent;
    private INVALID_VALUE = -1;
    private route: ActivatedRoute;

    constructor(protected assessmentsService: TextAssessmentService, protected accountService: AccountService) {
        console.warn('Test Constructor');
    }

    setComponentRoute(route: ActivatedRoute) {
        this.route = route;
        this.subscribeToRouteParameters();
    }

    sendAssessmentEvent(eventType: TextAssessmentEventType, feedbackType: FeedbackType | undefined, textBlockType: TextBlockType | undefined) {
        this.eventToSend.setEventType(eventType).setFeedbackType(feedbackType).setSegmentType(textBlockType);
        lastValueFrom(this.assessmentsService.submitAssessmentEvent(this.eventToSend));
    }

    subscribeToRouteParameters() {
        this.route.params.subscribe((params) => {
            console.log(params);
            this.userId = this.accountService.userIdentity ? Number(this.accountService.userIdentity.id) : this.INVALID_VALUE;
            this.courseId = Number(params['courseId']);
            this.textExerciseId = Number(params['exerciseId']);
            this.participationId = Number(params['participationId']);
            this.submissionId = Number(params['submissionId']);
            this.eventToSend = new TextAssessmentEvent(this.userId, this.courseId, this.textExerciseId, this.participationId, this.submissionId);
        });
    }

    // sendAssessmentEvent(eventType: TextAssessmentEventType, feedbackType: FeedbackType, segmentType: TextBlockType) {
    //     this.eventToSend
    //         .setEventType(eventType)
    //         .setFeedbackType(feedbackType)
    //         .setSegmentType(segmentType);
    //     lastValueFrom(this.assessmentsService.submitAssessmentEvent(this.eventToSend));
    // }

    // async sendAssessmentEventDelete() {
    //     const assessmentEventToSend: TextAssessmentEvent = {
    //         userId: this.userId,
    //         eventType: TextAssessmentEventType.DELETE_AUTOMATIC_FEEDBACK,
    //         feedbackType: FeedbackType.AUTOMATIC,
    //         segmentType: TextBlockType.AUTOMATIC,
    //         courseId: 2,
    //         textExerciseId: 3,
    //         submissionId: 4,
    //     };
    //     await lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    // }
    //
    // sendAssessmentEventEditFeedback() {
    //     const assessmentEventToSend: TextAssessmentEvent = {
    //         userId: this.userId,
    //         eventType: TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK,
    //         feedbackType: FeedbackType.AUTOMATIC,
    //         segmentType: TextBlockType.AUTOMATIC,
    //         courseId: 2,
    //         textExerciseId: 3,
    //         submissionId: 4,
    //     };
    //     lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    // }
    //
    // sendAssessmentEventOnHoverWarning() {
    //     const assessmentEventToSend: TextAssessmentEvent = {
    //         userId: this.userId,
    //         eventType: TextAssessmentEventType.HOVER_OVER_IMPACT_WARNING,
    //         feedbackType: FeedbackType.AUTOMATIC,
    //         segmentType: TextBlockType.AUTOMATIC,
    //         courseId: 2,
    //         textExerciseId: 3,
    //         submissionId: 4,
    //     };
    //     lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    // }
    //
    // sendAssessmentEventOnConflictClicked() {
    //     const assessmentEventToSend: TextAssessmentEvent = {
    //         userId: this.userId,
    //         eventType: TextAssessmentEventType.CLICK_TO_RESOLVE_CONFLICT,
    //         feedbackType: FeedbackType.AUTOMATIC,
    //         segmentType: TextBlockType.AUTOMATIC,
    //         courseId: 2,
    //         textExerciseId: 3,
    //         submissionId: 4,
    //     };
    //     lastValueFrom(this.assessmentsService.submitAssessmentEvent(assessmentEventToSend));
    // }
}
