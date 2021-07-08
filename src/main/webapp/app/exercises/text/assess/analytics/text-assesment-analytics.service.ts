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
    private eventToSend: TextAssessmentEvent = new TextAssessmentEvent();
    private INVALID_VALUE = -1;
    private route: ActivatedRoute;

    constructor(protected assessmentsService: TextAssessmentService, protected accountService: AccountService) {}

    setComponentRoute(route: ActivatedRoute) {
        this.route = route;
        this.subscribeToRouteParameters();
    }

    sendAssessmentEvent(eventType: TextAssessmentEventType, feedbackType: FeedbackType | undefined = undefined, textBlockType: TextBlockType | undefined = undefined) {
        this.eventToSend.setEventType(eventType).setFeedbackType(feedbackType).setSegmentType(textBlockType);
        lastValueFrom(this.assessmentsService.submitAssessmentEvent(this.eventToSend));
    }

    subscribeToRouteParameters() {
        this.route.params.subscribe((params) => {
            this.userId = this.accountService.userIdentity ? Number(this.accountService.userIdentity.id) : this.INVALID_VALUE;
            this.courseId = Number(params['courseId']);
            this.textExerciseId = Number(params['exerciseId']);
            this.participationId = Number(params['participationId']);
            this.submissionId = Number(params['submissionId']);
            this.eventToSend = new TextAssessmentEvent(this.userId, this.courseId, this.textExerciseId, this.participationId, this.submissionId);
        });
    }
}
