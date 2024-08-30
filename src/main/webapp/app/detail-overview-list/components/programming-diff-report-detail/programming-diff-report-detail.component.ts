import { Component, Input } from '@angular/core';
import type { ProgrammingDiffReportDetail } from 'app/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { GitDiffReportModule } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ExerciseDetailDirective } from 'app/detail-overview-list/exercise-detail.directive';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-programming-diff-report-detail',
    templateUrl: 'programming-diff-report-detail.component.html',
    standalone: true,
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseActionsModule,
        GitDiffReportModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseLifecycleModule,
        AssessmentInstructionsModule,
        ArtemisModelingEditorModule,
        ExerciseDetailDirective,
    ],
})
export class ProgrammingDiffReportDetailComponent {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly TooltipPlacement = TooltipPlacement;

    protected readonly WARNING = ButtonType.WARNING;

    protected readonly faCodeCompare = faCodeCompare;

    @Input() detail: ProgrammingDiffReportDetail;

    constructor(private modalService: NgbModal) {}

    showGitDiff(gitDiff?: ProgrammingExerciseGitDiffReport) {
        if (!gitDiff) {
            return;
        }

        const modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: 'diff-view-modal' });
        modalRef.componentInstance.report = gitDiff;
    }
}
