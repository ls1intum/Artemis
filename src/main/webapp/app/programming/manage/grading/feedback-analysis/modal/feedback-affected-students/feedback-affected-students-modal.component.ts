import { Component, effect, inject, input, signal, untracked } from '@angular/core';

import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/foundation/service/alert.service';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-affected-students-modal',
    templateUrl: './feedback-affected-students-modal.component.html',
    imports: [CodeButtonComponent, TranslateDirective],
    providers: [FeedbackAnalysisService],
})
export class AffectedStudentsModalComponent {
    activeModal = inject(NgbActiveModal);
    feedbackService = inject(FeedbackAnalysisService);
    alertService = inject(AlertService);

    courseId = input.required<number>();
    exerciseId = input.required<number>();
    feedbackDetail = input.required<FeedbackDetail>();
    readonly participation = signal<FeedbackAffectedStudentDTO[]>([]);
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.affectedStudentsModal';

    readonly faSpinner = faSpinner;
    protected readonly RepositoryType = RepositoryType;
    readonly isLoading = signal<boolean>(false);

    constructor() {
        effect(() => {
            void untracked(async () => {
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
