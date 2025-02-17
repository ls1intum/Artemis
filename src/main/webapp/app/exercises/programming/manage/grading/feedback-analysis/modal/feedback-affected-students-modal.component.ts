import { Component, effect, inject, input, signal, untracked } from '@angular/core';

import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-affected-students-modal',
    templateUrl: './feedback-affected-students-modal.component.html',
    imports: [CodeButtonComponent, TranslateDirective],
    providers: [FeedbackAnalysisService],
})
export class AffectedStudentsModalComponent {
    courseId = input.required<number>();
    exerciseId = input.required<number>();
    feedbackDetail = input.required<FeedbackDetail>();
    readonly participation = signal<FeedbackAffectedStudentDTO[]>([]);
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.affectedStudentsModal';

    readonly faSpinner = faSpinner;
    readonly isLoading = signal<boolean>(false);

    activeModal = inject(NgbActiveModal);
    feedbackService = inject(FeedbackAnalysisService);
    alertService = inject(AlertService);

    constructor() {
        effect(() => {
            untracked(async () => {
                await this.loadAffected();
            });
        });
    }

    private async loadAffected() {
        const feedbackDetail = this.feedbackDetail();
        this.isLoading.set(true);
        try {
            const response = await this.feedbackService.getParticipationForFeedbackDetailText(this.exerciseId(), feedbackDetail.feedbackIds);
            this.participation.set(response);
        } catch (error) {
            this.alertService.error(this.TRANSLATION_BASE + '.error');
        } finally {
            this.isLoading.set(false);
        }
    }
}
