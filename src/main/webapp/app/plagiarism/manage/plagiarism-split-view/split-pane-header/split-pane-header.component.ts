import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, input } from '@angular/core';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { Subject, Subscription } from 'rxjs';
import { TextPlagiarismFileElement } from 'app/plagiarism/shared/entities/text/TextPlagiarismFileElement';
import { NgbDropdown, NgbDropdownItem } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    imports: [NgbDropdown, NgClass, FaIconComponent, NgbDropdownItem, TranslateDirective, ArtemisTranslatePipe],
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

    private fileSelectSubscription?: Subscription;
    private showFilesSubscription?: Subscription;
    private dropdownHoverSubscription?: Subscription;

    // Icons
    faChevronDown = faChevronDown;
    hoveredFileIndex: number;

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
        this.fileSelectSubscription = this.fileSelectedSubject()!.subscribe((textPlagiarismElement) => {
            if (this.isLockFilesEnabled()) {
                this.handleLockedFileSelection(textPlagiarismElement.file, textPlagiarismElement.idx);
            }
        });
    }

    private handleLockedFileSelection(file: FileWithHasMatch, idx: number): void {
        const index = this.files[idx]?.file === file.file ? idx : this.getIndexOf(file);

        if (index >= 0) {
            this.handleFileSelect(file, index, false);
        } else {
            this.showFiles = false;
        }
    }

    /**
     * subscribes to listening onto dropdown toggle in component instance
     * @private helper method
     */
    private subscribeToShowFiles(): void {
        this.showFilesSubscription = this.showFilesSubject()?.subscribe((showFiles) => {
            if (this.isLockFilesEnabled()! || (!this.isLockFilesEnabled()! && !showFiles)) {
                this.toggleShowFiles(false, showFiles);
            }
        });
    }

    /**
     * subscribes to listening onto mouse enter changes in dropdown in component instance
     * @private helper method
     */
    private subscribeToDropdownHover(): void {
        this.dropdownHoverSubscription = this.dropdownHoverSubject()?.subscribe((textPlagiarismElement) => {
            if (this.isLockFilesEnabled()) {
                this.handleDropdownHover(textPlagiarismElement.file, textPlagiarismElement.idx);
            }
        });
    }

    private handleDropdownHover(file: FileWithHasMatch, idx: number): void {
        const index = this.files[idx]?.file === file.file ? idx : this.getIndexOf(file);

        this.hoveredFileIndex = index >= 0 ? index : -1;
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
        this.dropdownHoverSubscription?.unsubscribe();
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
     * @param propagateChanges propagate changes to listeners subscribed to fileSelectedSubject
     */
    handleFileSelect(file: FileWithHasMatch, idx: number, propagateChanges: boolean): void {
        if (propagateChanges) {
            this.fileSelectedSubject()!.next({ idx: idx, file: file });
            file.hasMatch = true;
        }
        this.activeFileIndex = idx;
        this.showFiles = false;
        this.selectFile.emit(file.file);
    }

    hasFiles(): boolean {
        return !!this.files?.length;
    }

    /**
     * Toggles the dropdown visibility and optionally propagates changes to the parent component.
     * @param showFiles Optional toggle status; if undefined, the status will be toggled.
     * @param propagateChanges Whether to propagate the change to the parent component.
     */
    toggleShowFiles(propagateChanges: boolean, showFiles?: boolean): void {
        if (this.hasFiles()) {
            this.showFiles = showFiles !== undefined ? showFiles : !this.showFiles;

            if (propagateChanges) {
                this.showFilesSubject()!.next(this.showFiles);
            }
        }
    }

    triggerMouseEnter(file: FileWithHasMatch, idx: number) {
        const subject = this.dropdownHoverSubject();
        if (subject) {
            subject.next({ idx, file });
        }
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
