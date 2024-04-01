import { Component, EventEmitter, Input, OnChanges, Output, QueryList, SimpleChanges, ViewChild, ViewChildren, ViewEncapsulation } from '@angular/core';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { firstValueFrom } from 'rxjs';
import { Annotation, FileSession } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import {
    CommitState,
    CreateFileChange,
    DeleteFileChange,
    EditorState,
    FileChange,
    FileType,
    RenameFileChange,
} from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { fromPairs, pickBy } from 'lodash-es';

@Component({
    selector: 'jhi-code-editor-monaco',
    templateUrl: './code-editor-monaco.component.html',
    styleUrls: ['./code-editor-monaco.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [RepositoryFileService],
})
export class CodeEditorMonacoComponent implements OnChanges {
    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;
    @ViewChildren(CodeEditorTutorAssessmentInlineFeedbackComponent)
    inlineFeedbackComponents: QueryList<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    @Input()
    commitState: CommitState;
    @Input()
    editorState: EditorState;
    @Input()
    course?: Course;
    @Input()
    feedbacks: Feedback[] = [];
    @Input()
    isTutorAssessment: boolean = false;
    @Input()
    selectedFile: string | undefined = undefined;
    @Input()
    sessionId: number | string;
    @Input()
    set buildAnnotations(buildAnnotations: Array<Annotation>) {
        this.setBuildAnnotations(buildAnnotations);
    }

    annotationsArray: Array<Annotation> = [];

    @Output()
    onFileContentChange: EventEmitter<{ file: string; fileContent: string }> = new EventEmitter<{ file: string; fileContent: string }>();

    isLoading = false;

    fileSession: FileSession = {};

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        private fileService: CodeEditorFileService,
        protected localStorageService: LocalStorageService,
    ) {}

    async ngOnChanges(changes: SimpleChanges): Promise<void> {
        const editorWasRefreshed = changes.editorState && changes.editorState.previousValue === EditorState.REFRESHING && this.editorState === EditorState.CLEAN;
        // Refreshing the editor resets any local files.
        if (editorWasRefreshed) {
            this.fileSession = {};
            this.editor.reset();
        }
        if ((changes.selectedFile && this.selectedFile) || editorWasRefreshed) {
            await this.selectFileInEditor(this.selectedFile);
            this.setBuildAnnotations(this.annotationsArray);
            this.renderFeedbackWidgets();
        }
    }

    async selectFileInEditor(fileName: string | undefined): Promise<void> {
        if (!fileName) {
            // There is nothing to be done, as the editor will be hidden when there is no file.
            return;
        }
        if (!this.fileSession[fileName]) {
            this.isLoading = true;
            const fileContent = await firstValueFrom(this.repositoryFileService.getFile(fileName)).then((fileObj) => fileObj.fileContent);
            this.fileSession[fileName] = { code: fileContent, loadingError: false, cursor: { column: 0, row: 0 } };
            this.isLoading = false;
        }

        this.editor.changeModel(fileName, this.fileSession[fileName].code);
        this.editor.setPosition(this.fileSession[fileName].cursor);
    }

    onFileTextChanged(text: string): void {
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            const previousText = this.fileSession[this.selectedFile].code;
            if (previousText !== text) {
                this.fileSession[this.selectedFile] = { code: text, loadingError: false, cursor: this.editor.getPosition() };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: text });
            }
        }
    }

    protected renderFeedbackWidgets() {
        for (const feedback of this.filterFeedbackForSelectedFile([...this.feedbacks])) {
            this.addLineWidgetWithFeedback(feedback);
        }
    }

    getInlineFeedbackNode(line: number) {
        return [...this.inlineFeedbackComponents].find((c) => c.codeLine === line)?.elementRef?.nativeElement;
    }

    private addLineWidgetWithFeedback(feedback: Feedback): void {
        const line = Feedback.getReferenceLine(feedback);
        if (!line) {
            throw new Error('No line found for feedback ' + feedback.id);
        }
        // In the future, there may be more than one feedback node per line.
        const feedbackNode = this.getInlineFeedbackNode(line);
        // The lines are 0-based for Ace, but 1-based for Monaco -> increase by 1 to ensure it works in both editors.
        this.editor.addLineWidget(line + 1, 'feedback-' + feedback.id, feedbackNode);
    }

    /**
     * Returns the feedbacks that refer to the currently selected file, or an empty array if no file is selected.
     * @param feedbacks The feedbacks to filter.
     */
    filterFeedbackForSelectedFile(feedbacks: Feedback[]): Feedback[] {
        if (!this.selectedFile) {
            return [];
        }
        return feedbacks.filter((feedback) => feedback.reference && Feedback.getReferenceFilePath(feedback) === this.selectedFile);
    }

    /**
     * Updates the state of the fileSession based on a change made to the files themselves (not the content).
     * - If a file was renamed, references to it are updated to use its new name.
     * - If a file was deleted, references to it are removed.
     * - If a file was created, a new reference is created.
     * Afterwards, the build annotations are updated to reflect this new change.
     * @param fileChange The change made: renaming, deleting, or creating a file.
     */
    async onFileChange(fileChange: FileChange) {
        if (fileChange instanceof RenameFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            for (const annotation of this.annotationsArray) {
                if (annotation.fileName === fileChange.oldFileName) {
                    annotation.fileName = fileChange.newFileName;
                }
            }
            this.storeAnnotations([fileChange.newFileName]);
        } else if (fileChange instanceof DeleteFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            this.storeAnnotations([fileChange.fileName]);
        } else if (fileChange instanceof CreateFileChange && fileChange.fileType === FileType.FILE) {
            this.fileSession = { ...this.fileSession, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 }, loadingError: false } };
        }
        this.setBuildAnnotations(this.annotationsArray);
    }

    /*
     * Taken from code-editor-ace.component.ts
     */
    /**
     * Saves the updated annotations to local storage
     * @param savedFiles
     */
    storeAnnotations(savedFiles: string[]) {
        const toUpdate = fromPairs(this.annotationsArray.filter((a) => savedFiles.includes(a.fileName)).map((a) => [a.hash, a]));
        const toKeep = pickBy(this.loadAnnotations(), (a) => !savedFiles.includes(a.fileName));

        this.localStorageService.store(
            'annotations-' + this.sessionId,
            JSON.stringify({
                ...toKeep,
                ...toUpdate,
            }),
        );
    }

    /**
     * Loads annotations from local storage
     */
    loadAnnotations() {
        return JSON.parse(this.localStorageService.retrieve('annotations-' + this.sessionId) || '{}');
    }

    setBuildAnnotations(buildAnnotations: Annotation[]): void {
        if (buildAnnotations.length > 0 && this.selectedFile) {
            const sessionAnnotations = this.loadAnnotations();
            this.annotationsArray = buildAnnotations.map((a) => {
                const hash = a.fileName + a.row + a.column + a.text;
                if (sessionAnnotations[hash] == undefined || sessionAnnotations[hash].timestamp < a.timestamp) {
                    return { ...a, hash };
                } else {
                    return sessionAnnotations[hash];
                }
            });
        } else {
            this.annotationsArray = buildAnnotations;
        }
        this.editor.setAnnotations(
            buildAnnotations.filter((buildAnnotation) => buildAnnotation.fileName === this.selectedFile),
            this.commitState === CommitState.UNCOMMITTED_CHANGES,
        );
    }

    // Expose to template
    protected readonly Feedback = Feedback;
    protected readonly CommitState = CommitState;
}
