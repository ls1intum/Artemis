import { Component, Input, inject } from '@angular/core';
import type { ProgrammingDiffReportDetail } from 'app/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { GitDiffReportModule } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-programming-diff-report-detail',
    templateUrl: 'programming-diff-report-detail.component.html',
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, GitDiffReportModule],
})
export class ProgrammingDiffReportDetailComponent {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly TooltipPlacement = TooltipPlacement;
    protected readonly WARNING = ButtonType.WARNING;

    protected readonly faCodeCompare = faCodeCompare;

    private readonly modalService = inject(NgbModal);

    @Input({ required: true }) detail: ProgrammingDiffReportDetail;

    showGitDiff(gitDiff?: ProgrammingExerciseGitDiffReport) {
        if (!gitDiff) {
            return;
        }

        const modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: 'diff-view-modal' });
        modalRef.componentInstance.report = gitDiff;
    }
}
