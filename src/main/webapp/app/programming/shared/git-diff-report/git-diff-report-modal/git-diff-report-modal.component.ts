import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
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

    readonly repositoryDiffInformation = input.required<RepositoryDiffInformation>();

    readonly diffForTemplateAndSolution = input<boolean>(true);

    close(): void {
        this.activeModal.dismiss();
    }
}
