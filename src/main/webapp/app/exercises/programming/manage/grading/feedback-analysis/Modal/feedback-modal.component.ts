import { Component, inject, input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-feedback-modal',
    templateUrl: './feedback-modal.component.html',
    imports: [TranslateDirective],
})
export class FeedbackModalComponent {
    feedbackDetail = input.required<FeedbackDetail>();
    activeModal = inject(NgbActiveModal);
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis';
}
