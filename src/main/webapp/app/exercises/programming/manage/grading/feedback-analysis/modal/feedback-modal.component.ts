import { Component, OnInit, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-feedback-modal',
    templateUrl: './feedback-modal.component.html',
    imports: [TranslateDirective, CommonModule],
})
export class FeedbackModalComponent implements OnInit {
    feedbackDetail = input.required<FeedbackDetail>();
    longFeedbackText = signal<string>('');

    activeModal = inject(NgbActiveModal);
    longFeedbackTextService = inject(LongFeedbackTextService);
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis';

    ngOnInit(): void {
        if (this.feedbackDetail().hasLongFeedbackText) {
            this.longFeedbackTextService.find(this.feedbackDetail().feedbackIds[0]).subscribe((response) => {
                this.longFeedbackText.set(response.body || this.feedbackDetail().detailTexts[0]);
            });
        }
    }
}
