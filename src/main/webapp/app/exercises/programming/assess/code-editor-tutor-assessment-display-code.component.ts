import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-display-code',
    templateUrl: './code-editor-tutor-assessment-display-code.component.html',
    styleUrls: ['./code-editor-tutor-assessment-display-code.component.scss'],
})
export class CodeEditorTutorAssessmentDisplayCodeComponent implements OnChanges {
    @Input()
    selectedFile: string;
    @Input()
    allFeedbacks: Feedback[] = [];
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};
    studentCode: string;
    studentCodePerLine: string[];

    isLoading = false;

    // manual assessment
    fileFeedbacks: Feedback[];
    toggleInlineComment = false;
    lineOfCodeForInlineComment: number;
    fileFeedbackPerLine: { [line: number]: Feedback } = {};

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
            this.studentCodePerLine = this.studentCode.split(/\r?\n/);
            if (!this.allFeedbacks) {
                this.allFeedbacks = [];
            }
            this.fileFeedbacks = this.allFeedbacks.filter((feedback) => feedback.reference && feedback.reference.includes(this.selectedFile));
            this.fileFeedbackPerLine = {};
            this.fileFeedbacks.forEach((feedback) => {
                const line: number = +feedback.reference!.split('line:')[1];
                this.fileFeedbackPerLine[line] = feedback;
            });
            console.log('file feebacks normal und per line und undefined');
            console.log(this.allFeedbacks);
            console.log(this.fileFeedbacks);
            console.log(this.fileFeedbackPerLine);
            console.log(this.fileFeedbackPerLine[1]);
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

    openInlineFeedback(i: number) {
        this.lineOfCodeForInlineComment = i;
        this.toggleInlineComment = true;
        console.log('openInlineFeedback toggleInline: ' + this.toggleInlineComment + ' line of code: ' + this.lineOfCodeForInlineComment);
        console.log('feeedbacks');
        console.log(this.fileFeedbacks);
        console.log(this.allFeedbacks);
    }

    updateFeedback(feedback: Feedback) {
        console.log('updateFeedback');
        const line: number = +feedback.reference!.split('line:')[1];
        // Check if feedback already exists and update it, else append it to feedbacks of the file
        if (this.allFeedbacks.some((f) => f.reference === feedback.reference)) {
            console.log('update existing feedback');
            const index = this.allFeedbacks.findIndex((f) => f.reference === feedback.reference);
            this.allFeedbacks[index] = feedback;
            this.fileFeedbackPerLine[line] = feedback;
        } else {
            console.log('append feedback');
            this.allFeedbacks.push(feedback);
            this.fileFeedbackPerLine[line] = feedback;
        }
        console.log('fileFeedbacks after update 123: ');
        console.log(this.fileFeedbackPerLine);
        console.log(this.allFeedbacks);
        this.toggleInlineComment = false;
        this.onUpdateFeedback.emit(this.allFeedbacks);
        console.log('should emit');
    }

    onCancelFeedback() {
        this.toggleInlineComment = false;
    }

    deleteFeedback(feedback: Feedback) {
        const indexToDelete = this.allFeedbacks.indexOf(feedback);
        const line: number = +feedback.reference!.split('line:')[1];
        this.allFeedbacks.splice(indexToDelete, 1);
        delete this.fileFeedbackPerLine[line];
        this.onUpdateFeedback.emit(this.allFeedbacks);
    }
}
