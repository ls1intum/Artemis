import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

interface GitDiffReportModalData {
    repositoryDiffInformation?: RepositoryDiffInformation;
    diffForTemplateAndSolution?: boolean;
}

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffReportComponent, TranslateDirective],
})
export class GitDiffReportModalComponent {
    static readonly WINDOW_CLASS = 'diff-view-modal';

    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);
    private readonly data = this.dialogConfig.data as GitDiffReportModalData | undefined;

    readonly repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(this.data?.repositoryDiffInformation);

    readonly diffForTemplateAndSolution = signal<boolean>(this.data?.diffForTemplateAndSolution ?? true);

    close(): void {
        this.dialogRef.close();
    }
}
