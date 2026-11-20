import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TumAetUiDialogConfig, TumAetUiDialogRef, TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

export interface GitDiffReportModalData {
    repositoryDiffInformation?: RepositoryDiffInformation;
    diffForTemplateAndSolution?: boolean;
}

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffReportComponent, TumAetUiProgressSpinnerComponent, ArtemisTranslatePipe],
})
export class GitDiffReportModalComponent {
    private readonly dialogRef = inject(TumAetUiDialogRef);
    private readonly dialogConfig = inject<TumAetUiDialogConfig<GitDiffReportModalData>>(TumAetUiDialogConfig);
    private readonly data = this.dialogConfig.data;

    readonly repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(this.data?.repositoryDiffInformation);

    readonly diffForTemplateAndSolution = signal<boolean>(this.data?.diffForTemplateAndSolution ?? true);

    close(): void {
        this.dialogRef.close();
    }
}
