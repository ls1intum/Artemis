import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumAetUiTagComponent, TumAetUiTagSeverity } from '@tumaet/ui-angular/tag';
import { DiffInformation, FileStatus } from 'app/programming/shared/utils/diff.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-git-diff-file-panel-title',
    templateUrl: './git-diff-file-panel-title.component.html',
    styleUrls: ['./git-diff-file-panel-title.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumAetUiTagComponent],
})
export class GitDiffFilePanelTitleComponent {
    readonly diffInformation = input.required<DiffInformation>();

    protected readonly FileStatus = FileStatus;

    /** Colour of the status tag: created files are green, renamed ones yellow and deleted ones red. */
    protected readonly statusSeverity = computed<TumAetUiTagSeverity>(() => {
        switch (this.diffInformation().fileStatus) {
            case FileStatus.CREATED:
                return 'success';
            case FileStatus.RENAMED:
                return 'warn';
            case FileStatus.DELETED:
                return 'danger';
            default:
                return 'secondary';
        }
    });
}
