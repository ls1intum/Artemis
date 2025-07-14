import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TextResultComponent } from 'app/text/overview/text-result/text-result.component';
import { FEEDBACK_EXAMPLES } from 'app/core/user/settings/learner-profile/feedback-learner-profile/onboarding-modal/feedback-examples';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { LearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/learner-profile-dto.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-feedback-onboarding-modal',
    standalone: true,
    templateUrl: './feedback-onboarding-modal.component.html',
    styleUrls: ['./feedback-onboarding-modal.component.scss'],
    imports: [CommonModule, TextResultComponent, TranslateDirective],
})
export class FeedbackOnboardingModalComponent {
    @Input() profileMissing = false;
    @Output() onboardingCompleted = new EventEmitter<void>();
    step = 0;
    readonly totalSteps = 2;
    selected: (number | null)[] = [null, null];
    feedbackExamples = FEEDBACK_EXAMPLES;

    private activeModal = inject(NgbActiveModal);
    private learnerProfileApiService = inject(LearnerProfileApiService);
    private alertService = inject(AlertService);
    protected translateService = inject(TranslateService);

    next() {
        if (this.step < this.totalSteps - 1) {
            this.step++;
        }
    }
    back() {
        if (this.step > 0) {
            this.step--;
        }
    }
    select(step: number, choice: number) {
        if (this.selected[step] === choice) {
            this.selected[step] = null;
        } else {
            this.selected[step] = choice;
        }
    }
    close() {
        this.activeModal.close();
    }
    async finish() {
        try {
            const mapValue = (val: number | null) => (val === null ? 2 : val === 0 ? 1 : 3);
            const newProfile = new LearnerProfileDTO({
                feedbackAlternativeStandard: mapValue(this.selected[0]),
                feedbackFollowupSummary: mapValue(this.selected[1]),
                feedbackBriefDetailed: mapValue(this.selected[2]),
                hasSetupFeedbackPreferences: true,
            });
            if (this.profileMissing) {
                await this.learnerProfileApiService.postLearnerProfile(newProfile);
            } else {
                const profile = await this.learnerProfileApiService.getLearnerProfileForCurrentUser();
                newProfile.id = profile.id;
                await this.learnerProfileApiService.putUpdatedLearnerProfile(newProfile);
            }
            this.alertService.closeAll();
            this.alertService.addAlert({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
            this.onboardingCompleted.emit();
            this.activeModal.close();
        } catch (error) {
            this.handleError(error);
            this.activeModal.close();
        }
    }

    /**
     * Handles errors that occur during API calls.
     * @param error - The error that occurred
     */
    private handleError(error: unknown): void {
        if (error instanceof HttpErrorResponse) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: error.message,
            });
        } else {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        }
    }
}
