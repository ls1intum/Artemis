import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';

/**
 * A file name that additionally stores if a plagiarism match has been found for it.
 */
export type FileWithHasMatch = {
    file: string;
    hasMatch: boolean;
};

@Component({
    selector: 'jhi-split-pane-header',
    templateUrl: './split-pane-header.component.html',
    styleUrls: ['./split-pane-header.component.scss'],
})
export class SplitPaneHeaderComponent implements OnChanges {
    @Input() files: FileWithHasMatch[];
    @Input() studentLogin: string;
    @Output() selectFile = new EventEmitter<string>();

    public showFiles = false;
    public activeFileIndex = 0;

    // Icons
    faChevronDown = faChevronDown;

    ngOnChanges(changes: SimpleChanges) {
        if (changes.files) {
            const files: FileWithHasMatch = changes.files.currentValue;

            this.activeFileIndex = 0;

            if (this.hasFiles()) {
                this.selectFile.emit(files[0].file);
            }
        }
    }

    hasActiveFile(): boolean {
        return this.hasFiles() && this.activeFileIndex < this.files.length;
    }

    getActiveFile(): string {
        return this.files[this.activeFileIndex].file;
    }

    handleFileSelect(file: FileWithHasMatch, idx: number): void {
        this.activeFileIndex = idx;
        this.showFiles = false;
        this.selectFile.emit(file.file);
    }

    hasFiles(): boolean {
        return this.files && this.files.length > 0;
    }

    toggleShowFiles(): void {
        if (this.hasFiles()) {
            this.showFiles = !this.showFiles;
        }
    }
}
