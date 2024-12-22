import { ChangeDetectionStrategy, Component, ViewContainerRef, inject, signal, viewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective],
})
export class GitDiffReportModalComponent {
    static readonly WINDOW_CLASS = 'diff-view-modal';

    private readonly activeModal = inject(NgbActiveModal);

    public readonly diffForTemplateAndSolution = signal<boolean>(true);

    public readonly container = viewChild.required('container', { read: ViewContainerRef });

    public close(): void {
        this.activeModal.dismiss();
    }
}
