import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FileStatus } from 'app/programming/shared/git-diff-report/model/git-diff.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-git-diff-file-panel-title',
    templateUrl: './git-diff-file-panel-title.component.html',
    styleUrls: ['./git-diff-file-panel-title.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, NgClass],
})
export class GitDiffFilePanelTitleComponent {
    readonly originalFilePath = input<string>();
    readonly modifiedFilePath = input<string>();
    readonly fileStatus = input<FileStatus>();
    readonly title = input<string>();

    readonly titleAndFileStatus = computed(() => {this.title, this.fileStatus});

    protected readonly FileStatus = FileStatus;
}
