import { Component, OnInit, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { LongFeedbackTextApi } from 'app/openapi/api/long-feedback-text-api';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-feedback-modal',
    templateUrl: './feedback-modal.component.html',
    imports: [TranslateDirective, CommonModule],
})
export class FeedbackModalComponent implements OnInit {
    activeModal = inject(NgbActiveModal);
    longFeedbackTextApi = inject(LongFeedbackTextApi);

    feedbackDetail = input.required<FeedbackDetail>();
    longFeedbackText = signal<string>('');

    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis';

    ngOnInit(): void {
        if (this.feedbackDetail().hasLongFeedbackText) {
            this.longFeedbackTextApi.getLongFeedback(this.feedbackDetail().feedbackIds[0]).subscribe((longFeedback) => {
                this.longFeedbackText.set(longFeedback || this.feedbackDetail().detailTexts[0]);
            });
        }
    }
}
