import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TextResultComponent } from 'app/text/overview/text-result/text-result.component';
import { FEEDBACK_EXAMPLES } from './feedback-examples';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearnerProfileApiService } from '../learner-profile-api.service';
import { LearnerProfileDTO } from '../dto/learner-profile-dto.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-feedback-onboarding-modal',
    standalone: true,
    templateUrl: './feedback-onboarding-modal.component.html',
    styleUrls: ['./feedback-onboarding-modal.component.scss'],
    imports: [CommonModule, TextResultComponent],
})
export class FeedbackOnboardingModalComponent {
    step = 0;
    readonly totalSteps = 3;
    selected: (number | null)[] = [null, null, null];
    feedbackExamples = FEEDBACK_EXAMPLES;

    private activeModal = inject(NgbActiveModal);
    private learnerProfileApiService = inject(LearnerProfileApiService);
    private alertService = inject(AlertService);
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
            const profile = await this.learnerProfileApiService.getLearnerProfileForCurrentUser();
            const mapValue = (val: number | null) => (val === null ? 2 : val === 0 ? 1 : 3);
            const updatedProfile = new LearnerProfileDTO({
                id: profile.id,
                feedbackAlternativeStandard: mapValue(this.selected[0]),
                feedbackFollowupSummary: mapValue(this.selected[1]),
                feedbackBriefDetailed: mapValue(this.selected[2]),
            });
            await this.learnerProfileApiService.putUpdatedLearnerProfile(updatedProfile);
            this.alertService.closeAll();
            this.alertService.addAlert({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
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
