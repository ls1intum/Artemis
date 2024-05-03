import { Component, Input, OnInit } from '@angular/core';

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
})
export class GitDiffFilePanelTitleComponent implements OnInit {
    @Input()
    previousFilePath?: string;

    @Input()
    filePath?: string;

    title?: string;
    fileStatus: FileStatus = FileStatus.UNCHANGED;

    ngOnInit(): void {
        if (this.filePath && this.previousFilePath) {
            if (this.filePath !== this.previousFilePath) {
                this.title = `${this.previousFilePath} → ${this.filePath}}`;
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

    protected readonly FileStatus = FileStatus;
}
