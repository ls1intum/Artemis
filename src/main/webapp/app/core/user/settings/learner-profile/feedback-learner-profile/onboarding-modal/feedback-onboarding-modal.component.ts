import { Component, inject, signal } from '@angular/core';
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
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-feedback-onboarding-modal',
    standalone: true,
    templateUrl: './feedback-onboarding-modal.component.html',
    styleUrls: ['./feedback-onboarding-modal.component.scss'],
    imports: [CommonModule, TextResultComponent, TranslateDirective, ButtonComponent],
})
export class FeedbackOnboardingModalComponent {
    onboardingCompleted = signal<undefined>(undefined);
    step = 0;
    readonly totalSteps = 2;
    selected: (number | undefined)[] = [undefined, undefined];
    feedbackExamples = FEEDBACK_EXAMPLES;

    private activeModal = inject(NgbActiveModal);
    private learnerProfileApiService = inject(LearnerProfileApiService);
    private alertService = inject(AlertService);
    protected translateService = inject(TranslateService);
    protected themeService = inject(ThemeService);

    // Button types and sizes for template
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    /**
     * Navigates to the next step in the onboarding process.
     */
    next() {
        if (this.step < this.totalSteps - 1) {
            this.step++;
        }
    }

    /**
     * Navigates to the previous step in the onboarding process.
     */
    back() {
        if (this.step > 0) {
            this.step--;
        }
    }

    /**
     * Selects a choice for a given step.
     * @param step - The step to select a choice for
     * @param choice - The choice to select
     */
    select(step: number, choice: number) {
        if (this.selected[step] === choice) {
            this.selected[step] = undefined;
        } else {
            this.selected[step] = choice;
        }
    }

    /**
     * Closes the modal.
     */
    close() {
        this.activeModal.close();
    }

    /**
     * Maps selection index to feedback value
     * @param selection - The selected option index (0 or 1)
     * @returns 1 for first option, 3 for second option, 2 as default
     */
    private mapSelectionToFeedbackValue(selection: number | undefined): number {
        if (selection === 0) return 1;
        if (selection === 1) return 3;
        return 2;
    }

    /**
     * Finishes the onboarding process.
     */
    async finish() {
        try {
            const newProfile = new LearnerProfileDTO({
                feedbackDetail: this.mapSelectionToFeedbackValue(this.selected[0]),
                feedbackFormality: this.mapSelectionToFeedbackValue(this.selected[1]),
                hasSetupFeedbackPreferences: true,
            });
            const profile = await this.learnerProfileApiService.getLearnerProfileForCurrentUser();
            newProfile.id = profile.id;
            await this.learnerProfileApiService.putUpdatedLearnerProfile(newProfile);
            this.alertService.closeAll();
            this.alertService.addAlert({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
            this.onboardingCompleted.set(undefined);
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
