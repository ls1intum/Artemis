import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, input } from '@angular/core';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { Subject, Subscription } from 'rxjs';
import { TextPlagiarismFileElement } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismFileElement';

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
    fileSelectedSubject = input<Subject<TextPlagiarismFileElement>>();
    isLockFilesEnabled = input<boolean>();
    showFilesSubject = input<Subject<boolean>>();
    dropdownHoverSubject = input<Subject<TextPlagiarismFileElement>>();

    @Output() selectFile = new EventEmitter<string>();

    public showFiles = false;
    public activeFileIndex = 0;

    private fileSelectSubscription: Subscription;
    private showFilesSubscription: Subscription;

    // Icons
    faChevronDown = faChevronDown;
    hoveredFileIdx: number;

    ngOnInit(): void {
        this.subscribeToFileSelection();
        this.subscribeToShowFiles();
        this.subscribeToDropdownHover();
    }

    /**
     * subscribes to listening onto file changes in component instance
     * @private helper method
     */
    private subscribeToFileSelection(): void {
        this.fileSelectSubscription = this.fileSelectedSubject()!.subscribe((val) => {
            if (this.isLockFilesEnabled()) {
                this.handleLockedFileSelection(val.file, val.idx);
            }
        });
    }

    private handleLockedFileSelection(file: FileWithHasMatch, idx: number): void {
        let index;
        if (this.files[idx]?.file === file.file) {
            this.handleFileSelectWithoutPropagation(file, idx);
        } else if ((index = this.getIndexOf(file)) >= 0) {
            this.handleFileSelectWithoutPropagation(file, index);
        } else {
            this.showFiles = false;
        }
    }

    /**
     * subscribes to listening onto dropdown toggle in component instance
     * @private helper method
     */
    private subscribeToShowFiles(): void {
        this.showFilesSubscription = this.showFilesSubject()!.subscribe((showFiles) => {
            if (this.isLockFilesEnabled()! || (!this.isLockFilesEnabled()! && !showFiles)) {
                this.toggleShowFilesWithoutPropagation(showFiles);
            }
        });
    }

    /**
     * subscribes to listening onto mouse enter changes in dropdown in component instance
     * @private helper method
     */
    private subscribeToDropdownHover(): void {
        this.dropdownHoverSubject()!.subscribe((val) => {
            if (this.isLockFilesEnabled()) {
                this.handleDropdownHover(val.file, val.idx);
            }
        });
    }

    private handleDropdownHover(file: FileWithHasMatch, idx: number): void {
        let index;
        if (this.files[idx]?.file === file.file) {
            this.hoveredFileIdx = idx;
        } else if ((index = this.getIndexOf(file)) >= 0) {
            this.hoveredFileIdx = index;
        } else this.hoveredFileIdx = -1;
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
       this.fileSelectSubscription?.unsubscribe();

      this.showFilesSubscription?.unsubscribe();
    }

    hasActiveFile(): boolean {
        return this.hasFiles() && this.activeFileIndex < this.files.length;
    }

    getActiveFile(): string {
        return this.files[this.activeFileIndex].file;
    }

    /**
     * handles selection of file from dropdown, propagates change to fileslectionsubject component for lock sync
     * @param file to be selected
     * @param idx index of the file from the dropdown
     */
    handleFileSelect(file: FileWithHasMatch, idx: number): void {
        this.fileSelectedSubject()!.next({ idx: idx, file: file });
        this.activeFileIndex = idx;
        this.showFiles = false;
        this.selectFile.emit(file.file);
    }

    /**
     * handles selection of file from dropdown, do NOT propagates change to fileslectionsubject component for lock sync
     * @param file to be selected
     * @param idx index of the file from the dropdown
     */
    handleFileSelectWithoutPropagation(file: FileWithHasMatch, idx: number) {
        this.activeFileIndex = idx;
        this.showFiles = false;
        this.selectFile.emit(file.file);
        file.hasMatch = true;
    }

    hasFiles(): boolean {
        return this.files?.length ? true :  false;
    }

    toggleShowFiles(): void {
        if (this.hasFiles()) {
            this.showFiles = !this.showFiles;
            this.showFilesSubject()!.next(this.showFiles);
        }
    }

    /**
     * handles toggle of the dropdown, do NOT propagate change to emit toggle to parent component component for lock sync
     * @param showFiles dropdown toggle status
     */
    toggleShowFilesWithoutPropagation(showFiles: boolean): void {
        if (this.hasFiles()) {
            this.showFiles = showFiles;
        }
    }

    triggerMouseEnter(file: FileWithHasMatch, idx: number) {
        this.dropdownHoverSubject()!.next({ idx: idx, file: file });
    }

    /**
     * gets index of the file if it exists
     * @param file The file to look up.
     * @returns index if found, -1 otherwise
     */
    private getIndexOf(file: FileWithHasMatch): number {
        return this.files.findIndex((f) => f.file === file.file);
    }
}
