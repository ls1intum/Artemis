import { Injectable } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextAssessmentEvent, TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { AccountService } from 'app/core/auth/account.service';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { tap, filter } from 'rxjs/operators';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Location } from '@angular/common';

/**
 * A service used to manage sending TextAssessmentEvent's to the server
 */
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
    public analyticsEnabled = false;

    constructor(protected assessmentsService: TextAssessmentService, protected accountService: AccountService, private profileService: ProfileService, public location: Location) {
        // retrieve the analytics enabled status from the profile info and set to current property
        this.profileService
            .getProfileInfo()
            .pipe(
                filter(Boolean),
                tap((info: ProfileInfo) => {
                    this.analyticsEnabled = Boolean(info.textAssessmentAnalyticsEnabled);
                }),
            )
            .subscribe();
    }

    /**
     * Angular services cannot inject route listeners automatically. The route needs to be injected manually, and then
     * it can be listened upon.
     * @param route the route instance of the component using the service
     */
    setComponentRoute(route: ActivatedRoute) {
        if (this.analyticsEnabled) {
            this.route = route;
            this.subscribeToRouteParameters();
        }
    }

    /**
     * Checks if artemis analytics is enabled, and then submits the prepared event to the server through the TextAssessmentService.
     * @param eventType type of the event to be sent
     * @param feedbackType type of the feedback to be sent. It is undefined by default to support simple events too.
     * @param textBlockType type of the text block to be sent. It is undefined by default to support simple events too.
     */
    sendAssessmentEvent(eventType: TextAssessmentEventType, feedbackType: FeedbackType | undefined = undefined, textBlockType: TextBlockType | undefined = undefined) {
        if (this.analyticsEnabled && !this.isExampleSubmissionRoute()) {
            this.eventToSend.setEventType(eventType);
            this.eventToSend.setFeedbackType(feedbackType);
            this.eventToSend.setSegmentType(textBlockType);
            this.assessmentsService.addTextAssessmentEvent(this.eventToSend).subscribe({
                error: (e) => console.error('Error sending statistics: ' + e.message),
            });
        }
    }

    private isExampleSubmissionRoute() {
        return !!this.location?.path().includes('example-submission');
    }
    /**
     * Subscribes to the route parameters and updates the respective id's accordingly.
     * Avoids having to set the id on the component's side.
     * @private
     */
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
