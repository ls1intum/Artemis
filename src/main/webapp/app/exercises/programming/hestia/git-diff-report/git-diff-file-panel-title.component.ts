import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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
    imports: [TranslateDirective],
})
export class GitDiffFilePanelTitleComponent implements OnInit {
    @Input()
    previousFilePath?: string;

    @Input()
    filePath?: string;

    title?: string;
    fileStatus: FileStatus = FileStatus.UNCHANGED;

    // Expose to template
    protected readonly FileStatus = FileStatus;

    ngOnInit(): void {
        if (this.filePath && this.previousFilePath) {
            if (this.filePath !== this.previousFilePath) {
                this.title = `${this.previousFilePath} → ${this.filePath}`;
                this.fileStatus = FileStatus.RENAMED;
            } else {
                this.title = this.filePath;
                this.fileStatus = FileStatus.UNCHANGED;
            }
        } else if (this.filePath) {
            this.title = this.filePath;
            this.fileStatus = FileStatus.CREATED;
        } else {
            this.title = this.previousFilePath;
            this.fileStatus = FileStatus.DELETED;
        }
    }
}
