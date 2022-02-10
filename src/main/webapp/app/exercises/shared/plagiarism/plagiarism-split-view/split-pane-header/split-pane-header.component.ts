import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-split-pane-header',
    templateUrl: './split-pane-header.component.html',
    styleUrls: ['./split-pane-header.component.scss'],
})
export class SplitPaneHeaderComponent implements OnChanges {
    @Input() files: string[];
    @Input() studentLogin: string;
    @Output() selectFile = new EventEmitter<string>();

    public showFiles = false;
    public activeFileIndex = 0;

    // Icons
    faChevronDown = faChevronDown;

    ngOnChanges(changes: SimpleChanges) {
        if (changes.files) {
            const files = changes.files.currentValue;

            this.activeFileIndex = 0;

            if (this.hasFiles()) {
                this.selectFile.emit(files[0]);
            }
        }
    }

    getActiveFile() {
        return this.hasFiles() && this.activeFileIndex < this.files.length && this.files[this.activeFileIndex];
    }

    handleFileSelect(file: string, idx: number) {
        this.activeFileIndex = idx;
        this.showFiles = false;
        this.selectFile.emit(file);
    }

    hasFiles() {
        return this.files && this.files.length > 0;
    }

    toggleShowFiles() {
        if (this.hasFiles()) {
            this.showFiles = !this.showFiles;
        }
    }
}
