import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';

@Component({
    selector: 'jhi-feedback-modal',
    templateUrl: './feedback-modal.component.html',
    imports: [ArtemisSharedCommonModule],
})
export class FeedbackModalComponent implements OnInit {
    feedbackDetail = input.required<FeedbackDetail>();
    activeModal = inject(NgbActiveModal);
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis';
    longFeedbackText = signal<string | undefined>(this.feedbackDetail().detailTexts[0]);
    longFeedbackTextService = inject(LongFeedbackTextService);

    ngOnInit(): void {
        if (this.feedbackDetail().hasLongFeedbackText) {
            this.longFeedbackTextService.loadLongFeedback(this.feedbackDetail().feedbackIds[0]).subscribe((response) => {
                this.longFeedbackText.set(response.body || this.feedbackDetail().detailTexts[0]);
            });
        }
    }
}
