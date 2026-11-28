import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular/progress-spinner';
import { TumAetUiTableDirective } from '@tumaet/ui-angular/table-directive';
import { TumAetUiTableLayoutComponent } from '@tumaet/ui-angular/table-layout';
import { Component, effect, inject, input, output, signal, untracked } from '@angular/core';

import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-affected-students-modal',
    templateUrl: './feedback-affected-students-modal.component.html',
    imports: [
        CodeButtonComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiDialogComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
    ],
    providers: [FeedbackAnalysisService],
})
export class AffectedStudentsModalComponent {
    feedbackService = inject(FeedbackAnalysisService);
    alertService = inject(AlertService);

    courseId = input.required<number>();
    exerciseId = input.required<number>();
    feedbackDetail = input.required<FeedbackDetail>();
    /** Emits once the dialog was closed, so the host can remove it. */
    closed = output<void>();
    readonly participation = signal<FeedbackAffectedStudentDTO[]>([]);
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.affectedStudentsModal';

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

    protected onVisibleChange(visible: boolean): void {
        if (!visible) {
            this.closed.emit();
        }
    }
}
