import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, QueryList, SimpleChanges, ViewChild, ViewChildren, ViewEncapsulation, inject } from '@angular/core';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { CodeEditorRepositoryFileService, ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { firstValueFrom, timeout } from 'rxjs';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER, Feedback } from 'app/entities/feedback.model';
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
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback-suggestion.component';
import { MonacoEditorLineHighlight } from 'app/shared/monaco-editor/model/monaco-editor-line-highlight.model';
import { FileTypeService } from 'app/exercises/programming/shared/service/file-type.service';
import { EditorPosition } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

type FileSession = { [fileName: string]: { code: string; cursor: EditorPosition; loadingError: boolean } };
export type Annotation = { fileName: string; row: number; column: number; text: string; type: string; timestamp: number; hash?: string };
@Component({
    selector: 'jhi-code-editor-monaco',
    templateUrl: './code-editor-monaco.component.html',
    styleUrls: ['./code-editor-monaco.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [RepositoryFileService],
})
export class CodeEditorMonacoComponent implements OnChanges {
    private repositoryFileService = inject(CodeEditorRepositoryFileService);
    private fileService = inject(CodeEditorFileService);
    protected localStorageService = inject(LocalStorageService);
    private changeDetectorRef = inject(ChangeDetectorRef);
    private fileTypeService = inject(FileTypeService);

    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;
    @ViewChildren(CodeEditorTutorAssessmentInlineFeedbackComponent)
    inlineFeedbackComponents: QueryList<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    @ViewChildren(CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent)
    inlineFeedbackSuggestionComponents: QueryList<CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent>;
    @Input()
    commitState: CommitState;
    @Input()
    editorState: EditorState;
    @Input()
    course?: Course;
    @Input()
    feedbacks: Feedback[] = [];
    @Input()
    feedbackSuggestions: Feedback[] = [];
    @Input()
    readOnlyManualFeedback: boolean;
    @Input()
    highlightDifferences: boolean;
    @Input()
    isTutorAssessment = false;
    @Input()
    disableActions = false;
    @Input()
    selectedFile?: string;
    @Input()
    sessionId: number | string;
    @Input()
    set buildAnnotations(buildAnnotations: Array<Annotation>) {
        this.setBuildAnnotations(buildAnnotations);
    }

    annotationsArray: Array<Annotation> = [];

    @Output()
    onError: EventEmitter<string> = new EventEmitter();
    @Output()
    onFileContentChange: EventEmitter<{ file: string; fileContent: string }> = new EventEmitter<{ file: string; fileContent: string }>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    @Output()
    onFileLoad = new EventEmitter<string>();
    @Output()
    onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output()
    onDiscardSuggestion = new EventEmitter<Feedback>();
    @Output()
    onHighlightLines = new EventEmitter<MonacoEditorLineHighlight[]>();

    editorLocked = false;
    /**
     * The number of currently loading files. If this number is greater than 0, the editor is in a loading state and hides its content.
     */
    loadingCount = 0;

    fileSession: FileSession = {};
    newFeedbackLines: number[] = [];
    binaryFileSelected = false;

    static readonly CLASS_DIFF_LINE_HIGHLIGHT = 'monaco-diff-line-highlight';
    static readonly CLASS_FEEDBACK_HOVER_BUTTON = 'monaco-add-feedback-button';
    static readonly FILE_TIMEOUT = 10000;

    // Expose to template
    protected readonly Feedback = Feedback;
    protected readonly CommitState = CommitState;

    async ngOnChanges(changes: SimpleChanges): Promise<void> {
        const editorWasRefreshed = changes.editorState && changes.editorState.previousValue === EditorState.REFRESHING && this.editorState === EditorState.CLEAN;
        const editorWasReset = changes.commitState && changes.commitState.previousValue !== CommitState.UNDEFINED && this.commitState === CommitState.UNDEFINED;
        // Refreshing the editor resets any local files.
        if (editorWasRefreshed || editorWasReset) {
            this.fileSession = {};
            this.editor.reset();
        }
        if ((changes.selectedFile && this.selectedFile) || editorWasRefreshed) {
            await this.selectFileInEditor(this.selectedFile);
            this.setBuildAnnotations(this.annotationsArray);
            this.newFeedbackLines = [];
            this.renderFeedbackWidgets();
            if (this.isTutorAssessment && !this.readOnlyManualFeedback) {
                this.setupAddFeedbackButton();
            }
            this.onFileLoad.emit(this.selectedFile);
        }

        if (changes.feedbacks) {
            this.newFeedbackLines = [];
            this.renderFeedbackWidgets();
        }

        this.editorLocked =
            this.disableActions || this.isTutorAssessment || this.commitState === CommitState.CONFLICT || !this.selectedFile || !!this.fileSession[this.selectedFile]?.loadingError;

        this.editor.layout();
    }

    async selectFileInEditor(fileName: string | undefined): Promise<void> {
        if (!fileName) {
            // There is nothing to be done, as the editor will be hidden when there is no file.
            return;
        }
        this.loadingCount++;
        if (!this.fileSession[fileName] || this.fileSession[fileName].loadingError) {
            let fileContent = '';
            let loadingError = false;
            try {
                fileContent = await firstValueFrom(this.repositoryFileService.getFile(fileName).pipe(timeout(CodeEditorMonacoComponent.FILE_TIMEOUT))).then(
                    (fileObj) => fileObj.fileContent,
                );
            } catch (error) {
                loadingError = true;
                if (error.message === ConnectionError.message) {
                    this.onError.emit('loadingFailed' + error.message);
                } else {
                    this.onError.emit('loadingFailed');
                }
            }
            this.fileSession[fileName] = { code: fileContent, loadingError, cursor: { column: 0, lineNumber: 0 } };
        }

        const code = this.fileSession[fileName].code;
        this.binaryFileSelected = this.fileTypeService.isBinaryContent(code);

        // Since fetching the file may take some time, we need to check if the file is still selected.
        if (!this.binaryFileSelected && this.selectedFile === fileName) {
            this.editor.changeModel(fileName, code);
            this.editor.setPosition(this.fileSession[fileName].cursor);
        }
        this.loadingCount--;
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

    getText(): string {
        return this.editor.getText();
    }

    getNumberOfLines(): number {
        return this.editor.getNumberOfLines();
    }

    highlightLines(startLine: number, endLine: number) {
        this.editor.highlightLines(startLine, endLine, CodeEditorMonacoComponent.CLASS_DIFF_LINE_HIGHLIGHT);
        this.onHighlightLines.emit(this.getLineHighlights());
    }

    setupAddFeedbackButton(): void {
        this.editor.setLineDecorationsHoverButton(CodeEditorMonacoComponent.CLASS_FEEDBACK_HOVER_BUTTON, (lineNumber) => this.addNewFeedback(lineNumber));
    }

    /**
     * Adds a new feedback widget to the specified line and renders it. The text field will be focused automatically.
     * @param lineNumber The line (as shown in the editor) to render the widget in.
     */
    addNewFeedback(lineNumber: number): void {
        // TODO for a follow-up: in the future, there might be multiple feedback items on the same line.
        const lineNumberZeroBased = lineNumber - 1;
        if (!this.getInlineFeedbackNode(lineNumberZeroBased)) {
            this.newFeedbackLines.push(lineNumberZeroBased);
            this.renderFeedbackWidgets(lineNumberZeroBased);
        }
    }

    /**
     * Updates an existing feedback item and renders it. If necessary, an unsaved feedback item will be converted into an actual feedback item.
     * @param feedback The feedback item to save.
     */
    updateFeedback(feedback: Feedback) {
        const line = Feedback.getReferenceLine(feedback);
        const existingFeedbackIndex = this.feedbacks.findIndex((f) => f.reference === feedback.reference);
        if (existingFeedbackIndex !== -1) {
            // Existing feedback -> update only
            this.feedbacks[existingFeedbackIndex] = feedback;
        } else {
            // New feedback -> save as actual feedback.
            this.feedbacks.push(feedback);
            this.newFeedbackLines = this.newFeedbackLines.filter((l) => l !== line);
        }
        this.renderFeedbackWidgets();
        this.onUpdateFeedback.emit(this.feedbacks);
    }

    /**
     * Cancels the edit of a feedback item, removing the widget if necessary.
     * @param line The line the feedback item refers to.
     */
    cancelFeedback(line: number) {
        // We only have to remove new feedback.
        if (this.newFeedbackLines.includes(line)) {
            this.newFeedbackLines = this.newFeedbackLines.filter((l) => l !== line);
            this.renderFeedbackWidgets();
        }
    }

    /**
     * Removes an existing feedback item and renders the updated state.
     * @param feedback The feedback to remove.
     */
    deleteFeedback(feedback: Feedback) {
        this.feedbacks = this.feedbacks.filter((f) => !Feedback.areIdentical(f, feedback));
        this.onUpdateFeedback.emit(this.feedbacks);
        this.renderFeedbackWidgets();
    }

    /**
     * Accepts a feedback suggestion by storing a feedback suggestion as actual feedback.
     * @param feedback The feedback item of the feedback suggestion.
     */
    acceptSuggestion(feedback: Feedback): void {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((f) => f !== feedback);
        feedback.text = (feedback.text ?? FEEDBACK_SUGGESTION_IDENTIFIER).replace(FEEDBACK_SUGGESTION_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER);
        this.updateFeedback(feedback);
        this.onAcceptSuggestion.emit(feedback);
    }

    /**
     * Discards a feedback suggestion and removes its widget.
     * @param feedback The feedback item of the feedback suggestion.
     */
    discardSuggestion(feedback: Feedback): void {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((f) => f !== feedback);
        this.renderFeedbackWidgets();
        this.onDiscardSuggestion.emit(feedback);
    }

    /**
     * Renders the current state of feedback in the editor.
     * @param lineOfWidgetToFocus The line number of the widget whose text area should be focused.
     * @protected
     */
    protected renderFeedbackWidgets(lineOfWidgetToFocus?: number) {
        // Since the feedback widgets rely on the DOM nodes of each feedback item, Angular needs to re-render each node, hence the timeout.
        this.changeDetectorRef.detectChanges();
        setTimeout(() => {
            this.editor.disposeWidgets();
            for (const feedback of this.filterFeedbackForSelectedFile([...this.feedbacks, ...this.feedbackSuggestions])) {
                this.addLineWidgetWithFeedback(feedback);
            }

            // New, unsaved feedback has no associated object yet.
            for (const line of this.newFeedbackLines) {
                const feedbackNode = this.getInlineFeedbackNodeOrElseThrow(line);
                this.editor.addLineWidget(line + 1, 'feedback-new-' + line, feedbackNode);
            }

            // Focus the text area of the widget on the specified line if available.
            if (lineOfWidgetToFocus !== undefined) {
                this.getInlineFeedbackNode(lineOfWidgetToFocus)?.querySelector<HTMLTextAreaElement>('#feedback-textarea')?.focus();
            }
        }, 0);
    }

    /**
     * Retrieves the feedback node currently rendered at the specified line and throws an error if it is not available.
     * @param line The line (0-based) for which to retrieve the feedback node.
     */
    getInlineFeedbackNodeOrElseThrow(line: number): HTMLElement {
        const element = this.getInlineFeedbackNode(line);
        if (!element) {
            throw new Error('No feedback node found at line ' + line);
        }
        return element;
    }

    /**
     * Retrieves the feedback node currently rendered at the specified line, or undefined if it is not available.
     * @param line The line (0-based) for which to retrieve the feedback node.
     */
    getInlineFeedbackNode(line: number): HTMLElement | undefined {
        return [...this.inlineFeedbackComponents, ...this.inlineFeedbackSuggestionComponents].find((c) => c.codeLine === line)?.elementRef?.nativeElement;
    }

    private addLineWidgetWithFeedback(feedback: Feedback): void {
        const line = Feedback.getReferenceLine(feedback);
        if (line === undefined) {
            throw new Error('No line found for feedback ' + feedback.id);
        }
        // In the future, there may be more than one feedback node per line.
        const feedbackNode = this.getInlineFeedbackNodeOrElseThrow(line);
        // Feedback is stored with 0-based lines, but the lines of the Monaco editor used in Artemis are 1-based. We add 1 to correct this
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
            this.fileSession = { ...this.fileSession, [fileChange.fileName]: { code: '', cursor: { lineNumber: 0, column: 0 }, loadingError: false } };
        }
        this.setBuildAnnotations(this.annotationsArray);
    }

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

    getLineHighlights(): MonacoEditorLineHighlight[] {
        return this.editor.getLineHighlights();
    }
}
