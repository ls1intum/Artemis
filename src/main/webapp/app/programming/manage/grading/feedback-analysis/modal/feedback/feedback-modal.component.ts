import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { LongFeedbackTextApi } from 'app/openapi/api/long-feedback-text-api';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-feedback-modal',
    templateUrl: './feedback-modal.component.html',
    imports: [TranslateDirective, CommonModule, TumAetUiDialogComponent, ArtemisTranslatePipe],
})
export class FeedbackModalComponent implements OnInit {
    longFeedbackTextApi = inject(LongFeedbackTextApi);

    feedbackDetail = input.required<FeedbackDetail>();
    /** Emits once the dialog was closed, so the host can remove it. */
    closed = output<void>();
    longFeedbackText = signal<string>('');

    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis';

    ngOnInit(): void {
        if (this.feedbackDetail().hasLongFeedbackText) {
            this.longFeedbackTextApi.getLongFeedback(this.feedbackDetail().feedbackIds[0]).subscribe((longFeedback) => {
                this.longFeedbackText.set(longFeedback || this.feedbackDetail().detailTexts[0]);
            });
        }
    }

    protected onVisibleChange(visible: boolean): void {
        if (!visible) {
            this.closed.emit();
        }
    }
}
