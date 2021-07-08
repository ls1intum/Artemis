import { Injectable } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextAssessmentEvent, TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { AccountService } from 'app/core/auth/account.service';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';

type FeatureToggleState = {
    index: number;
    name: FeatureToggle;
    isActive: boolean;
};

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
    public availableToggles: FeatureToggleState[] = [];

    constructor(protected assessmentsService: TextAssessmentService, protected accountService: AccountService, private featureToggleService: FeatureToggleService) {
        this.featureToggleService
            .getFeatureToggles()
            .pipe(
                tap((activeToggles) => {
                    this.availableToggles = Object.values(FeatureToggle).map((name, index) => ({ name, index, isActive: activeToggles.includes(name) }));
                }),
            )
            .subscribe();
    }

    isArtemisAnalyticsFeatureEnabled(): boolean {
        if (Array.isArray(this.availableToggles) && this.availableToggles.length > 0) {
            return this.availableToggles.find((toggle) => toggle.name === FeatureToggle.ARTEMIS_ANALYTICS && toggle.isActive) !== undefined;
        }
        return false;
    }

    setComponentRoute(route: ActivatedRoute) {
        if (this.isArtemisAnalyticsFeatureEnabled()) {
            this.route = route;
            this.subscribeToRouteParameters();
        }
    }

    sendAssessmentEvent(eventType: TextAssessmentEventType, feedbackType: FeedbackType | undefined = undefined, textBlockType: TextBlockType | undefined = undefined) {
        if (this.isArtemisAnalyticsFeatureEnabled()) {
            this.eventToSend.setEventType(eventType).setFeedbackType(feedbackType).setSegmentType(textBlockType);
            this.assessmentsService.submitAssessmentEvent(this.eventToSend);
        }
    }

    private subscribeToRouteParameters() {
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
