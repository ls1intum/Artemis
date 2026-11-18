import { Component, OnDestroy, computed, inject, input } from '@angular/core';
import type { ProgrammingDiffReportDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared-ui/components/buttons/button/button.component';
import { faCodeCompare, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { GitDiffReportModalComponent, GitDiffReportModalData } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';

import { TumAetUiDialogRef, TumAetUiDialogService, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
@Component({
    selector: 'jhi-programming-diff-report-detail',
    templateUrl: 'programming-diff-report-detail.component.html',
    imports: [GitDiffLineStatComponent, ArtemisTranslatePipe, TumAetUiTooltipDirective, ButtonComponent, TranslateDirective, FaIconComponent],
})
export class ProgrammingDiffReportDetailComponent implements OnDestroy {
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly translateService = inject(TranslateService);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly TooltipPlacement = TooltipPlacement;
    protected readonly WARNING = ButtonType.WARNING;

    protected readonly faCodeCompare = faCodeCompare;
    protected readonly faSpinner = faSpinner;

    private dialogRef?: TumAetUiDialogRef;

    detail = input.required<ProgrammingDiffReportDetail>();

    private readonly repositoryDiffInformation = computed(() => this.detail().data.repositoryDiffInformation());
    readonly addedLineCount = computed(() => this.repositoryDiffInformation()?.totalLineChange?.addedLineCount ?? 0);
    readonly removedLineCount = computed(() => this.repositoryDiffInformation()?.totalLineChange?.removedLineCount ?? 0);
    readonly lineChangesLoading = computed(() => this.detail().data.lineChangesLoading());

    ngOnDestroy() {
        this.dialogRef?.close();
    }

    showGitDiff() {
        const repositoryDiffInformation = this.repositoryDiffInformation();
        if (!repositoryDiffInformation) {
            return;
        }

        this.dialogRef = this.dialogService.open<GitDiffReportModalComponent, unknown, GitDiffReportModalData>(GitDiffReportModalComponent, {
            header: this.translateService.instant('artemisApp.programmingExercise.diffReport.title'),
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            // Render the comparison wide so side-by-side diffs are readable without horizontal scrolling.
            size: 'full',
            data: {
                repositoryDiffInformation,
                diffForTemplateAndSolution: true,
            },
        });
    }
}
