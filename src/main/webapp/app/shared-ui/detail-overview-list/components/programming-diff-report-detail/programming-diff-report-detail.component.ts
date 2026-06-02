import { Component, OnDestroy, computed, inject, input } from '@angular/core';
import type { ProgrammingDiffReportDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared-ui/components/buttons/button/button.component';
import { faCodeCompare, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';

import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
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

    detail = input.required<ProgrammingDiffReportDetail>();

    private readonly detailData = computed(() => this.detail().data);

    get addedLineCount(): number {
        return this.detailData().repositoryDiffInformation?.totalLineChange?.addedLineCount ?? 0;
    }

    get removedLineCount(): number {
        return this.detailData().repositoryDiffInformation?.totalLineChange?.removedLineCount ?? 0;
    }

    get lineChangesLoading(): boolean {
        return this.detailData().lineChangesLoading ?? false;
    }

    ngOnDestroy() {
        this.dialogRef?.close();
    }

    showGitDiff() {
        const repositoryDiffInformation = this.detailData().repositoryDiffInformation;
        if (!repositoryDiffInformation) {
            return;
        }

        this.dialogRef =
            this.dialogService.open(GitDiffReportModalComponent, {
                header: this.translateService.instant('artemisApp.programmingExercise.diffReport.title'),
                modal: true,
                closable: true,
                closeOnEscape: true,
                dismissableMask: false,
                // Render the comparison wide so side-by-side diffs are readable without horizontal scrolling.
                width: '90vw',
                styleClass: GitDiffReportModalComponent.WINDOW_CLASS,
                data: {
                    repositoryDiffInformation,
                    diffForTemplateAndSolution: true,
                },
            }) ?? undefined;
    }
}
