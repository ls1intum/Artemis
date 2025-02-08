import { Component, inject, input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-confirm-feedback-channel-creation-modal',
    templateUrl: './confirm-feedback-channel-creation-modal.component.html',
    imports: [TranslateDirective],
})
export class ConfirmFeedbackChannelCreationModalComponent {
    protected readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.feedbackDetailChannel.confirmationModal';
    affectedStudentsCount = input.required<number>();
    private activeModal = inject(NgbActiveModal);

    confirm(): void {
        this.activeModal.close(true);
    }

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
