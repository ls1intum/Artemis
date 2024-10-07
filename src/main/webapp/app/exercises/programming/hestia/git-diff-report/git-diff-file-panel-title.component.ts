import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';

enum FileStatus {
    CREATED = 'created',
    RENAMED = 'renamed',
    DELETED = 'deleted',
    UNCHANGED = 'unchanged',
}
@Component({
    selector: 'jhi-git-diff-file-panel-title',
    templateUrl: './git-diff-file-panel-title.component.html',
    styleUrls: ['./git-diff-file-panel-title.component.scss'],
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, CommonModule],
})
export class GitDiffFilePanelTitleComponent {
    originalFilePath = input<string>();
    modifiedFilePath = input<string>();

    titleAndFileStatus = computed(() => this.getTitleAndFileStatus(this.originalFilePath(), this.modifiedFilePath()));

    title = computed(() => this.titleAndFileStatus().title);
    fileStatus = computed(() => this.titleAndFileStatus().fileStatus);

    protected readonly FileStatus = FileStatus;

    private getTitleAndFileStatus(originalFilePath?: string, modifiedFilePath?: string): { title?: string; fileStatus: FileStatus } {
        if (modifiedFilePath && originalFilePath) {
            if (modifiedFilePath !== originalFilePath) {
                return { title: `${originalFilePath} → ${modifiedFilePath}`, fileStatus: FileStatus.RENAMED };
            } else {
                return { title: modifiedFilePath, fileStatus: FileStatus.UNCHANGED };
            }
        } else if (modifiedFilePath) {
            return { title: modifiedFilePath, fileStatus: FileStatus.CREATED };
        } else {
            return { title: originalFilePath, fileStatus: FileStatus.DELETED };
        }
    }
}
