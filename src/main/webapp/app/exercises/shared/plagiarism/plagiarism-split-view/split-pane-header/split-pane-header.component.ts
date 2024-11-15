import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { Subject, Subscription } from 'rxjs';

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
export class SplitPaneHeaderComponent implements OnChanges, OnInit, OnDestroy {
    @Input() files: FileWithHasMatch[];
    @Input() studentLogin: string;
    @Input() fileSelectedSubject!: Subject<{ idx: number; file: FileWithHasMatch }>;
    @Input() isLockFilesEnabled!: boolean;

    @Output() selectFile = new EventEmitter<string>();

    public showFiles = false;
    public activeFileIndex = 0;

    fileSelectSubscription: Subscription;

    // Icons
    faChevronDown = faChevronDown;

    ngOnInit(): void {
        this.fileSelectSubscription = this.fileSelectedSubject.subscribe((val) => {
            console.log('passed file', val);
            console.log('passed and current index', val.idx, this.activeFileIndex);
            if (val.file && this.isLockFilesEnabled) {
                this.handleFileSelectWithoutPropagation(val.file, val.idx);
            }
        });
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.files) {
            const fileWithHasMatch: FileWithHasMatch[] = changes.files.currentValue;

            this.activeFileIndex = 0;

            if (this.hasFiles()) {
                this.selectFile.emit(fileWithHasMatch[0].file);
            }
        }
    }

    ngOnDestroy(): void {
        if (this.fileSelectSubscription) {
            this.fileSelectSubscription.unsubscribe();
        }
    }

    hasActiveFile(): boolean {
        return this.hasFiles() && this.activeFileIndex < this.files.length;
    }

    getActiveFile(): string {
        return this.files[this.activeFileIndex].file;
    }

    handleFileSelect(file: FileWithHasMatch, idx: number): void {
        this.fileSelectedSubject.next({ idx: idx, file: file });
        this.activeFileIndex = idx;
        this.showFiles = false;
        this.selectFile.emit(file.file);
    }

    handleFileSelectWithoutPropagation(file: FileWithHasMatch, idx: number) {
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
