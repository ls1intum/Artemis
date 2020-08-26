import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-display-code',
    templateUrl: './code-editor-tutor-assessment-display-code.component.html',
    styles: ['pre { display: inline; border: none; background-color: white; border-radius: unset ; padding: 1px; font-size: 11px} table {font-size: 11px}'],
})
export class CodeEditorTutorAssessmentDisplayCodeComponent implements OnChanges {
    @Input()
    selectedFile: string;
    @Output()
    onError = new EventEmitter<string>();

    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};
    studentCode: string;
    studentCodePerLine: string[];

    isLoading = false;

    constructor(private repositoryFileService: CodeEditorRepositoryFileService) {}

    /**
     * @function ngOnChanges
     * @desc New selectedFile      => load the file from the repository and opens it
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.selectedFile && this.selectedFile) {
            // Current file has changed
            // Only load the file from server if there is nothing stored in the file session
            if (this.selectedFile && !this.fileSession[this.selectedFile]) {
                this.loadFile(this.selectedFile);
            } else {
                this.initEditorAfterFileChange();
            }
        }
    }

    /**
     * Setup the component after a file change occurred.
     **/
    initEditorAfterFileChange() {
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            this.studentCode = this.fileSession[this.selectedFile].code;
            this.studentCodePerLine = this.studentCode.split('\n');
        }
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFile(fileName: string) {
        this.isLoading = true;
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService
            .getFile(fileName)
            .pipe(
                tap((fileObj) => {
                    this.fileSession[fileName] = { code: fileObj.fileContent, cursor: { column: 0, row: 0 } };
                    // It is possible that the selected file has changed - in this case don't update the editor.
                    if (this.selectedFile === fileName) {
                        this.initEditorAfterFileChange();
                    }
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }
}
