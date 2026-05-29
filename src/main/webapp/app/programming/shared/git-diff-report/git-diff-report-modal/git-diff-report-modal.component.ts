import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffReportComponent, TranslateDirective],
})
export class GitDiffReportModalComponent implements OnInit {
    static readonly WINDOW_CLASS = 'diff-view-modal';

    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    readonly repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(undefined);

    readonly diffForTemplateAndSolution = signal<boolean>(true);

    ngOnInit(): void {
        const data = this.dialogConfig?.data ?? {};
        if (data.repositoryDiffInformation !== undefined) {
            this.repositoryDiffInformation.set(data.repositoryDiffInformation);
        }
        if (data.diffForTemplateAndSolution !== undefined) {
            this.diffForTemplateAndSolution.set(data.diffForTemplateAndSolution);
        }
    }

    close(): void {
        this.dialogRef.close();
    }
}
