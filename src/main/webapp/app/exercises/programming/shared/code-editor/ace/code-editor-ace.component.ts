import { UndoManager, acequire } from 'brace';
import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/sh';
import 'brace/mode/markdown';
import 'brace/mode/haskell';
import 'brace/mode/ocaml';
import 'brace/mode/c_cpp';
import 'brace/mode/python';
import 'brace/mode/swift';
import 'brace/mode/yaml';
import 'brace/mode/makefile';
import 'brace/mode/kotlin';
import 'brace/mode/assembly_x86';
import 'brace/mode/vhdl';
import 'brace/theme/dreamweaver';
import 'brace/theme/dracula';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import {
    AfterViewInit,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    Output,
    QueryList,
    SimpleChanges,
    ViewChild,
    ViewChildren,
    ViewEncapsulation,
} from '@angular/core';
import { Subscription, firstValueFrom, fromEvent } from 'rxjs';
import { CommitState, CreateFileChange, DeleteFileChange, EditorState, FileChange, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorRepositoryFileService, ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { TextChange } from 'app/entities/text-change.model';
import { LocalStorageService } from 'ngx-webstorage';
import { fromPairs, pickBy } from 'lodash-es';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER, Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { faCircleNotch, faPlusSquare } from '@fortawesome/free-solid-svg-icons';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback-suggestion.component';

export type Annotation = { fileName: string; row: number; column: number; text: string; type: string; timestamp: number; hash?: string };
export type FileSession = { [fileName: string]: { code: string; cursor: { column: number; row: number }; loadingError: boolean } };

@Component({
    selector: 'jhi-code-editor-ace',
    templateUrl: './code-editor-ace.component.html',
    styleUrls: ['./code-editor-ace.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [RepositoryFileService],
})
export class CodeEditorAceComponent implements AfterViewInit, OnChanges, OnDestroy {
    @ViewChild('editor', { static: true })
    editor: AceEditorComponent;
    @ViewChildren(CodeEditorTutorAssessmentInlineFeedbackComponent)
    inlineFeedbackComponents: QueryList<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    @ViewChildren(CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent)
    inlineFeedbackSuggestionsComponents: QueryList<CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent>;
    @Input()
    selectedFile: string;
    @Input()
    sessionId: number | string;
    @Input()
    readOnlyManualFeedback: boolean;

    @Input()
    set annotations(annotations: Array<Annotation>) {
        this.setAnnotations(annotations);
    }

    @Input()
    readonly commitState: CommitState;
    @Input()
    readonly editorState: EditorState;
    @Input()
    isTutorAssessment = false;
    @Input()
    feedbacks: Feedback[] = [];
    @Input()
    feedbackSuggestions: Feedback[] = [];
    @Input()
    highlightDifferences: boolean;
    @Input()
    course?: Course;
    @Input()
    disableActions = false;

    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    @Output()
    onFileLoad = new EventEmitter<string>();
    @Output()
    onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output()
    onDiscardSuggestion = new EventEmitter<Feedback>();

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = acequire('ace/ext/modelist');
    // Line widgets for inline feedback
    readonly LineWidgets = acequire('ace/line_widgets').LineWidgets;

    readonly Range = acequire('ace/range').Range;

    /** Ace Editor Options **/
    editorMode: string; // string or mode object
    isLoading = false;
    annotationsArray: Array<Annotation> = [];
    annotationChange: Subscription;
    fileSession: FileSession = {};
    newFeedbackLines: number[] = []; // new feedback, which is not yet saved, but must already be displayed to be editable
    editorSession: any;
    markerIds: number[] = [];
    gutterHighlights: Map<number, string[]> = new Map<number, string[]>();
    tabSize = 4;

    // Detecting editor resize events
    resizeObserver: ResizeObserver;

    // Icons
    readonly faPlusSquare = faPlusSquare;
    readonly faCircleNotch = faCircleNotch;

    // Expose to template
    protected readonly Feedback = Feedback;

    /**
     * Filter feedback: Only referenced to current file
     */
    filterFeedbackForFile(feedbacks: Feedback[]): Feedback[] {
        if (!this.selectedFile) {
            return [];
        }
        return feedbacks.filter((feedback) => feedback.reference && Feedback.getReferenceFilePath(feedback) === this.selectedFile);
    }

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        private fileService: CodeEditorFileService,
        protected localStorageService: LocalStorageService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    /**
     * Set the theme and other editor options
     */
    ngAfterViewInit(): void {
        this.editor.getEditor().setOptions({
            animatedScroll: true,
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
        });
        if (this.isTutorAssessment) {
            this.editor.getEditor().setShowFoldWidgets(false);
        }
        if (!this.resizeObserver) {
            // When the editor is resized in width, the inline feedback text could wrap. This would cause its height to change and we need to adapt it:
            this.resizeObserver = new ResizeObserver(() => {
                if (!this.editorSession) return; // not yet initialized, nothing to do
                // Adjust height of all feedback line widgets
                this.updateLineWidgets();
            });
            this.resizeObserver.observe(this.editor.elementRef.nativeElement);
        }
    }

    /**
     * New clean state       => reset the editor and file update subscriptions
     * New selectedFile      => load the file from the repository and open it in the editor
     * @param {SimpleChanges} changes
     */
    async ngOnChanges(changes: SimpleChanges): Promise<void> {
        if (
            (changes.commitState && changes.commitState.previousValue !== CommitState.UNDEFINED && this.commitState === CommitState.UNDEFINED) ||
            (changes.editorState && changes.editorState.previousValue === EditorState.REFRESHING && this.editorState === EditorState.CLEAN)
        ) {
            this.fileSession = {};
            if (this.annotationChange) {
                this.annotationChange.unsubscribe();
            }
            this.editor.getEditor().getSession().setValue('');
        }
        if (
            (changes.selectedFile && this.selectedFile) ||
            (changes.editorState && changes.editorState.previousValue === EditorState.REFRESHING && this.editorState === EditorState.CLEAN)
        ) {
            // Current selected file has changed
            if (this.selectedFile) {
                await this.ensureLoadedThenInitEditorIfSelected(this.selectedFile);
            } else {
                await this.initEditor();
            }
        }
        if (changes.commitState && changes.commitState.currentValue === CommitState.CONFLICT) {
            this.editor.setReadOnly(true);
        } else if (changes.commitState && changes.commitState.previousValue === CommitState.CONFLICT && changes.commitState.currentValue !== CommitState.CONFLICT) {
            this.editor.setReadOnly(false);
        }
        if ((changes.feedbacks || changes.feedbackSuggestions) && this.editorSession) {
            // when feedback/suggestions change and initEditor was already called
            await this.updateLineWidgets();
        }
    }

    /**
     * Set up the ace editor after a file change occurred or the file finished loading.
     * Makes sure potential previous settings are restored and the correct language service is used.
     **/
    async initEditor() {
        // Set up editorSession for inline feedback using lineWidgets
        this.editorSession = this.editor.getEditor().getSession();

        if (!this.editorSession.widgetManager) {
            this.editorSession.widgetManager = new this.LineWidgets(this.editorSession);
            this.editorSession.widgetManager.attach(this.editor.getEditor());
        }
        // We first remove the annotationChange subscription so the initial setValue doesn't count as an insert
        if (this.annotationChange) {
            this.annotationChange.unsubscribe();
        }
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            this.editor.getEditor().getSession().setValue(this.fileSession[this.selectedFile].code);
            this.annotationChange = fromEvent(this.editor.getEditor().getSession(), 'change').subscribe(([change]) => {
                this.updateAnnotations(change);
            });

            // Restore the previous cursor position
            this.editor.getEditor().moveCursorToPosition(this.fileSession[this.selectedFile].cursor);
            this.editorMode = this.aceModeList.getModeForPath(this.selectedFile).name;
            this.editor.setMode(this.editorMode);
            this.editor.getEditor().resize();
            this.editor.getEditor().focus();
            // always scroll to the top, otherwise inline annotations might be placed incorrectly
            this.editor.getEditor().getSession().setScrollTop(0);
            // Reset the undo stack after file change, otherwise the user can undo back to the old file
            this.editor.getEditor().getSession().setUndoManager(new UndoManager());
            this.displayAnnotations();

            if (this.markerIds.length > 0) {
                this.markerIds.forEach((markerId) => this.editorSession.removeMarker(markerId));
                this.markerIds = [];
            }
            if (this.gutterHighlights.size > 0) {
                this.gutterHighlights.forEach((classes, row) => classes.forEach((className) => this.editorSession.removeGutterDecoration(row, className)));
                this.gutterHighlights.clear();
            }
            this.onFileLoad.emit(this.selectedFile);
        }
        // Remove open inline feedback widgets for new feedback
        this.newFeedbackLines = [];
        // Remove previous lineWidgets and create new ones
        await this.updateLineWidgets();
    }

    /**
     * Fetches the content of the file with the specified filename from the server.
     * Errors must be handled by the caller with a catch block.
     * @param fileName Name of the file to be fetched
     */
    async fetchFileContent(fileName: string): Promise<string> {
        return firstValueFrom(this.repositoryFileService.getFile(fileName)).then((fileObj) => fileObj.fileContent);
    }

    /**
     * Reloads the file with the specified filename from the server and overwrites the file session.
     * If there is an error loading the file, the file session is still updated, but the loadingError flag is set to true.
     * @param fileName Name of the file to be reloaded
     */
    async forceReloadFileContents(fileName: string) {
        return this.fetchFileContent(fileName)
            .then((code) => {
                this.fileSession[fileName] = { code, cursor: { column: 0, row: 0 }, loadingError: false };
            })
            .catch((error) => {
                this.fileSession[fileName] = { code: '', cursor: { column: 0, row: 0 }, loadingError: true };
                if (error.message === ConnectionError.message) {
                    this.onError.emit('loadingFailed' + error.message);
                } else {
                    this.onError.emit('loadingFailed');
                }
            });
    }

    /**
     * Ensures that the file with the specified filename exists in the file session.
     * If it doesn't, it is fetched from the server and added to the file session.
     * If there is an error loading the file, the file session is still updated, but the loadingError flag is set to true.
     * @param fileName Name of the file to be loaded
     */
    async ensureLoadedThenInitEditorIfSelected(fileName: string): Promise<void> {
        // If the file is not yet loaded or loading failed, fetch it from the server
        // If the file is already loaded, we don't need to load it again
        if (!this.fileSession[fileName] || this.fileSession[fileName].loadingError) {
            this.isLoading = true;
            await this.forceReloadFileContents(fileName);
            this.isLoading = false;
        }

        // Only initialize the editor if the selected file has not changed in between
        // - prevents console errors (see https://github.com/ls1intum/Artemis/pull/603)
        if (this.selectedFile === fileName) {
            await this.initEditor();
        }
    }

    async forceReloadAll(fileNames: string[]) {
        this.isLoading = true;
        await Promise.all(
            fileNames.map(async (fileName) => {
                await this.forceReloadFileContents(fileName);
                if (this.selectedFile === fileName) {
                    await this.initEditor();
                }
            }),
        );
        this.isLoading = false;
    }

    /**
     * Callback function for text changes in the Ace Editor.
     * Is used for updating the error annotations in the editor and giving the touched file the unsaved flag.
     * @param event {string} Current editor code
     */
    async onFileTextChanged(event: any) {
        const code = event as string;
        if (this.isTutorAssessment) {
            this.editor.setReadOnly(true);
            if (!this.readOnlyManualFeedback) {
                this.setupLineIcons();
            }
            await this.updateLineWidgets();
        }
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            if (this.fileSession[this.selectedFile].code !== code) {
                const cursor = this.editor.getEditor().getCursorPosition();
                this.fileSession[this.selectedFile] = { code, cursor, loadingError: false };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: code });
            }
        }
    }

    ngOnDestroy() {
        if (this.annotationChange) {
            this.annotationChange.unsubscribe();
        }
        // Remove all line widgets so that no old ones are displayed when the editor is opened again
        for (const widget of this.editorSession?.lineWidgets ?? []) {
            if (widget) {
                this.editorSession.widgetManager.removeLineWidget(widget);
            }
        }
        this.resizeObserver?.disconnect();
    }

    /**
     * Recalculate the position of annotations according to changes in the editor.
     * Annotations are affected by changes in previous rows for row updates,
     * in the same row and previous columns for column updates.
     * @param change
     */
    updateAnnotations(change: TextChange) {
        const {
            start: { row: rowStart, column: columnStart },
            end: { row: rowEnd, column: columnEnd },
            action,
        } = change;
        if (action === 'remove' || action === 'insert') {
            const sign = action === 'remove' ? -1 : 1;
            const updateRowDiff = sign * (rowEnd - rowStart);
            const updateColDiff = sign * (columnEnd - columnStart);

            this.annotationsArray = this.annotationsArray.map((a) => {
                return this.selectedFile !== a.fileName
                    ? a
                    : {
                          ...a,
                          row: a.row > rowStart ? a.row + updateRowDiff : a.row,
                          column: a.column > columnStart && a.row === rowStart && a.row === rowEnd ? a.column + updateColDiff : a.column,
                      };
            });
            this.displayAnnotations();
        }
    }

    /**
     * Set the annotations for the editor.
     * Checks for each annotation whether an updated version exists in local storage.
     * @param annotations The new annotations array
     */
    setAnnotations(annotations: Array<Annotation> = []) {
        if (annotations.length > 0) {
            const sessionAnnotations = this.loadAnnotations();
            this.annotationsArray = annotations.map((a) => {
                const hash = a.fileName + a.row + a.column + a.text;
                if (sessionAnnotations[hash] == undefined || sessionAnnotations[hash].timestamp < a.timestamp) {
                    return { ...a, hash };
                } else {
                    return sessionAnnotations[hash];
                }
            });
        } else {
            this.annotationsArray = annotations;
        }

        this.displayAnnotations();
    }

    /**
     * Highlights lines using a marker and / or a gutter decorator.
     * @param firstLine the first line to highlight
     * @param lastLine the last line to highlight
     * @param lineHightlightClassName the classname to use for the highlight of the line in the editor content area, or undefined if it should not be highlighted
     * @param gutterHightlightClassName the classname to use for the highlight of the line number gutter, or undefined if the gutter should not be highlighted
     */
    highlightLines(firstLine: number, lastLine: number, lineHightlightClassName: string | undefined, gutterHightlightClassName: string | undefined) {
        if (lineHightlightClassName) {
            this.markerIds.push(this.editorSession.addMarker(new this.Range(firstLine, 0, lastLine, 1), lineHightlightClassName, 'fullLine'));
        }
        if (gutterHightlightClassName) {
            for (let i = firstLine; i <= lastLine; ++i) {
                this.editorSession.addGutterDecoration(i, gutterHightlightClassName);
                this.gutterHighlights.computeIfAbsent(i, () => []).push(gutterHightlightClassName);
            }
        }
    }

    /**
     * Updates the fileSession and annotations objects for a file change. This function is called
     * by the parent container.
     * @param fileChange
     */
    async onFileChange(fileChange: FileChange) {
        if (fileChange instanceof RenameFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            for (const a of this.annotationsArray) {
                if (a.fileName === fileChange.oldFileName) {
                    a.fileName = fileChange.newFileName;
                }
            }
            this.storeAnnotations([fileChange.newFileName]);
        } else if (fileChange instanceof DeleteFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            this.annotationsArray = this.annotationsArray.filter((a) => a.fileName === fileChange.fileName);
            this.storeAnnotations([fileChange.fileName]);
        } else if (fileChange instanceof CreateFileChange && this.selectedFile === fileChange.fileName) {
            this.fileSession = { ...this.fileSession, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 }, loadingError: false } };
            await this.initEditor();
        }
        this.displayAnnotations();
    }

    /**
     * Saves the updated annotations to local storage
     * @param savedFiles
     */
    storeAnnotations(savedFiles: Array<string>) {
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

    /**
     * Updates the annotations in the editor
     */
    displayAnnotations() {
        this.editor
            .getEditor()
            .getSession()
            .setAnnotations(this.annotationsArray.filter((a) => a.fileName === this.selectedFile));
    }

    /**
     * Add the inline comment button to all visible gutters in the ace editor.
     * We use a MutualObserver to check if children of the gutter layer changes
     * in order to add the button to all gutters.
     */
    setupLineIcons() {
        const gutterContainer = document.querySelector('.ace_gutter-layer');
        const container = this.editor.getEditor().container;
        this.observerDom(gutterContainer!, () => {
            const gutters = container.querySelectorAll('.ace_gutter-cell');
            const buttonInlineComment = document.querySelector('.btn-inline-comment');
            gutters.forEach((gutter: HTMLElement) => {
                const clone = buttonInlineComment!.cloneNode(true);
                clone.addEventListener('click', async () => {
                    const lineToAddFeedbackIn = parseInt(gutter.innerText) - 1;
                    this.newFeedbackLines.push(lineToAddFeedbackIn);
                    await this.updateLineWidgets();
                });
                // TODO: Check whether this causes an issue when having annotations
                if (gutter.childElementCount < 1) {
                    gutter.appendChild(clone);
                }
            });
        });
    }

    /**
     * Get the component of an inline feedback DOM node.
     * @param line line of code where the feedback inline component is located
     */
    getInlineFeedbackNode(line: number): Element | undefined {
        return [...this.inlineFeedbackComponents, ...this.inlineFeedbackSuggestionsComponents].find((c) => c.codeLine === line)?.elementRef?.nativeElement;
    }

    /**
     * Add lineWidget for specific line of code.
     * (ACE line widget, needs to be manually handled because ACE is not well integrated into Angular)
     * @param line line of code where the feedback inline component will be added to.
     */
    addLineWidgetWithFeedback(line: number) {
        // Get the feedback DOM node
        const inlineFeedbackNode = this.getInlineFeedbackNode(line);
        if (!inlineFeedbackNode) {
            throw new Error("Couldn't find inline feedback node for line " + line + '. Make sure that it is rendered by Angular.');
        }
        const lineWidget = {
            row: line,
            fixedWidth: true,
            coverGutter: true,
            el: inlineFeedbackNode,
        };
        const displayedWidget = (this.editorSession.lineWidgets ?? [])[line];
        if (!displayedWidget) {
            this.editorSession.widgetManager.addLineWidget(lineWidget);
            lineWidget.el.querySelector('textarea')?.focus();
        }
    }

    /**
     * Add lineWidget for specific line of code.
     * (ACE line widget, needs to be manually handled because ACE is not well integrated into Angular)
     * @param line line of code where the feedback inline component will be added to.
     */
    removeLineWidget(line: number) {
        const widget = (this.editorSession.lineWidgets ?? [])[line];
        if (widget) {
            this.editorSession.widgetManager.removeLineWidget(widget);
        }
    }

    /**
     * Sync the line widgets of the ACE editor to the current feedbacks.
     */
    async updateLineWidgets() {
        // Make Angular update the DOM
        this.changeDetectorRef.detectChanges();
        // Wait for one render cycle for all DOM updates to be done
        await new Promise((resolve) => setTimeout(resolve, 0));
        // Remove all existing line widgets
        const linesWithWidgets = this.editorSession.lineWidgets?.map((l: any) => l?.row).filter((row: number | undefined) => row != undefined) ?? [];
        for (const line of linesWithWidgets) {
            this.removeLineWidget(line);
        }
        // Add line widgets for all feedbacks
        for (const feedback of this.filterFeedbackForFile([...this.feedbacks, ...this.feedbackSuggestions])) {
            const line = Feedback.getReferenceLine(feedback)!;
            this.addLineWidgetWithFeedback(line);
        }
        // Add line widgets for new feedback that is not yet saved
        for (const line of this.newFeedbackLines) {
            this.addLineWidgetWithFeedback(line);
        }
    }

    /**
     * Adjusts the height of the ace editor after the lineWidget (inline feedback component) changed size
     * @param line Line of code which has inline feedback (lineWidget)
     */
    adjustLineWidgetHeight(line: number) {
        const widget = this.editorSession.lineWidgets[line];
        this.editorSession.widgetManager.removeLineWidget(widget);
        this.editorSession.widgetManager.addLineWidget(widget);
    }

    /**
     * Called whenever an inline feedback element is emitted. Updates existing feedbacks or adds onto it
     * @param feedback Newly created inline feedback.
     */
    async updateFeedback(feedback: Feedback) {
        const line = Feedback.getReferenceLine(feedback)!;
        // Check if feedback already exists and update it, else append it to feedbacks of the file
        if (this.feedbacks.some((f) => f.reference === feedback.reference)) {
            const index = this.feedbacks.findIndex((f) => f.reference === feedback.reference);
            this.feedbacks[index] = feedback;
        } else {
            this.feedbacks.push(feedback);
            // the feedback isn't new anymore
            this.newFeedbackLines = this.newFeedbackLines.filter((l) => l !== line);
        }
        await this.updateLineWidgets();
        this.onUpdateFeedback.emit(this.feedbacks);
    }

    /**
     * Called whenever an inline feedback is cancelled. Removes it from ace editor or just aligns height.
     * @param line
     */
    async cancelFeedback(line: number) {
        if (this.newFeedbackLines.includes(line)) {
            // Just remove the new feedback
            this.newFeedbackLines = this.newFeedbackLines.filter((l) => l !== line);
            await this.updateLineWidgets();
        } else {
            // Feedback exists (not new) and we keep it, just re-align height
            this.adjustLineWidgetHeight(line);
        }
    }

    /**
     * Deletes a feedback from the feedbacks
     * @param feedback Feedback to be removed
     */
    deleteFeedback(feedback: Feedback) {
        this.feedbacks = this.feedbacks.filter((f) => !Feedback.areIdentical(f, feedback));
        this.removeLineWidget(Feedback.getReferenceLine(feedback)!);
        this.onUpdateFeedback.emit(this.feedbacks);
    }

    /**
     * Accept a feedback suggestion: Make it "real" feedback and remove the suggestion card
     */
    async acceptSuggestion(feedback: Feedback) {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((f) => f !== feedback); // Remove the suggestion card
        this.removeLineWidget(Feedback.getReferenceLine(feedback)!);
        // Change the prefix "FeedbackSuggestion:" to "FeedbackSuggestion:accepted:"
        feedback.text = (feedback.text ?? FEEDBACK_SUGGESTION_IDENTIFIER).replace(FEEDBACK_SUGGESTION_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER);
        await this.updateFeedback(feedback); // Make it "real" feedback
        this.onAcceptSuggestion.emit(feedback);
    }

    /**
     * Discard a feedback suggestion: Remove the suggestion card and emit the event
     */
    discardSuggestion(feedback: Feedback) {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((f) => f !== feedback); // Remove the suggestion card
        this.removeLineWidget(Feedback.getReferenceLine(feedback)!);
        this.onDiscardSuggestion.emit(feedback);
    }

    /**
     * Observes whether the children of the DOM element change.
     * We need this to detect changes in the gutter layer, to add the inline button to each gutter.
     * @param obj The DOM element on which we check if children change.
     * @param callback The method to be called when a change is detected.
     */
    observerDom(obj: Element, callback: () => void) {
        if (!obj || obj.nodeType !== 1) {
            return;
        }
        if (MutationObserver) {
            // define a new observer
            const mutationObserver = new MutationObserver(callback);

            // have the observer observe the gutter layer for changes in children
            mutationObserver.observe(obj, { childList: true, subtree: true });
        } else {
            obj.addEventListener('DOMNodeInserted', callback, false);
            obj.addEventListener('DOMNodeRemoved', callback, false);
        }
        callback();
    }

    updateTabSize(event: number) {
        this.tabSize = event;
    }
}
