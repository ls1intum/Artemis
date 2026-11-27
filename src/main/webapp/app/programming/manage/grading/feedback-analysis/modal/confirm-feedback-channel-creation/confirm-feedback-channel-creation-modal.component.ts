import { Component, input, output } from '@angular/core';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-confirm-feedback-channel-creation-modal',
    templateUrl: './confirm-feedback-channel-creation-modal.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiDialogComponent, TumAetUiButtonDirective],
})
export class ConfirmFeedbackChannelCreationModalComponent {
    protected readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.feedbackDetailChannel.confirmationModal';
    affectedStudentsCount = input.required<number>();
    /** Emits `true` when the creation was confirmed and `false` when the dialog was dismissed. */
    closed = output<boolean>();

    confirm(): void {
        this.closed.emit(true);
    }

    dismiss(): void {
        this.closed.emit(false);
    }

    protected onVisibleChange(visible: boolean): void {
        if (!visible) {
            this.dismiss();
        }
    }
}
