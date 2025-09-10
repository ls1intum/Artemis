import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DiffInformation, FileStatus } from 'app/programming/shared/utils/diff.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-git-diff-file-panel-title',
    templateUrl: './git-diff-file-panel-title.component.html',
    styleUrls: ['./git-diff-file-panel-title.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, NgClass],
})
export class GitDiffFilePanelTitleComponent {
    readonly diffInformation = input.required<DiffInformation>();

    protected readonly FileStatus = FileStatus;
}
