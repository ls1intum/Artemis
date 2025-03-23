import { Component, Input, OnDestroy, inject, signal } from '@angular/core';
import type { ProgrammingDiffReportDetail } from 'app/shared/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/programming-exercise-git-diff-report.model';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal.component';

import { NgbModal, NgbModalRef, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-diff-report-detail',
    templateUrl: 'programming-diff-report-detail.component.html',
    imports: [GitDiffLineStatComponent, ArtemisTranslatePipe, NgbTooltipModule, ButtonComponent, TranslateDirective],
})
export class ProgrammingDiffReportDetailComponent implements OnDestroy {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly TooltipPlacement = TooltipPlacement;
    protected readonly WARNING = ButtonType.WARNING;

    protected readonly faCodeCompare = faCodeCompare;

    private readonly modalService = inject(NgbModal);
    private modalRef?: NgbModalRef;

    @Input({ required: true }) detail: ProgrammingDiffReportDetail;

    ngOnDestroy() {
        this.modalRef?.close();
    }

    showGitDiff(gitDiff?: ProgrammingExerciseGitDiffReport) {
        if (!gitDiff) {
            return;
        }

        this.modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: GitDiffReportModalComponent.WINDOW_CLASS });
        this.modalRef.componentInstance.report = signal(gitDiff);
    }
}
