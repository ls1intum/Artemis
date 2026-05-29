import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffReportComponent, TranslateDirective],
})
export class GitDiffReportModalComponent {
    static readonly WINDOW_CLASS = 'diff-view-modal';

    private readonly activeModal = inject(NgbActiveModal);

    readonly repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(undefined);

    readonly diffForTemplateAndSolution = signal<boolean>(true);

    close(): void {
        this.activeModal.dismiss();
    }
}
