import { Component, OnDestroy, inject, input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import type { ProgrammingDiffReportDetail } from 'app/shared/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { faCodeCompare, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';

import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
@Component({
    selector: 'jhi-programming-diff-report-detail',
    templateUrl: 'programming-diff-report-detail.component.html',
    imports: [GitDiffLineStatComponent, ArtemisTranslatePipe, NgbTooltipModule, ButtonComponent, TranslateDirective, FaIconComponent],
})
export class ProgrammingDiffReportDetailComponent implements OnDestroy {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly TooltipPlacement = TooltipPlacement;
    protected readonly WARNING = ButtonType.WARNING;

    protected readonly faCodeCompare = faCodeCompare;
    protected readonly faSpinner = faSpinner;

    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);
    private dialogRef?: DynamicDialogRef;

    readonly detail = input.required<ProgrammingDiffReportDetail>();

    get addedLineCount(): number {
        return this.detail().data.repositoryDiffInformation?.totalLineChange?.addedLineCount ?? 0;
    }

    get removedLineCount(): number {
        return this.detail().data.repositoryDiffInformation?.totalLineChange?.removedLineCount ?? 0;
    }

    get lineChangesLoading(): boolean {
        return this.detail().data.lineChangesLoading ?? false;
    }

    ngOnDestroy() {
        this.dialogRef?.close();
    }

    showGitDiff() {
        const repositoryDiffInformation = this.detail().data.repositoryDiffInformation;
        if (!repositoryDiffInformation) {
            return;
        }

        this.dialogRef = this.dialogService.open(GitDiffReportModalComponent, {
            header: this.translateService.instant('artemisApp.programmingExercise.diffReport.title'),
            width: '90vw',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            styleClass: GitDiffReportModalComponent.WINDOW_CLASS,
            data: {
                repositoryDiffInformation,
                diffForTemplateAndSolution: true,
            },
        });
    }
}
