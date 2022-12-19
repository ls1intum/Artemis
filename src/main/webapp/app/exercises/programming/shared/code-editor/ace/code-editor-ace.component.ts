import { UndoManager, acequire } from 'brace';
import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
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
import { AceEditorComponent, MAX_TAB_SIZE } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { Subscription, fromEvent, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { CommitState, CreateFileChange, DeleteFileChange, EditorState, FileChange, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { TextChange } from 'app/entities/text-change.model';
import { LocalStorageService } from 'ngx-webstorage';
import { fromPairs, pickBy } from 'lodash-es';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { faFileAlt } from '@fortawesome/free-regular-svg-icons';
import { faCircleNotch, faGear, faPlusSquare } from '@fortawesome/free-solid-svg-icons';

export type Annotation = { fileName: string; row: number; column: number; text: string; type: string; timestamp: number; hash?: string | null };

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
    feedbacks: Feedback[];
    @Input()
    highlightDifferences: boolean;
    @Input()
    course?: Course;

    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    @Output()
    onFileLoad = new EventEmitter<string>();

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = acequire('ace/ext/modelist');
    // Line widgets for inline feedback
    readonly LineWidgets = acequire('ace/line_widgets').LineWidgets;

    readonly Range = acequire('ace/range').Range;

    readonly MAX_TAB_SIZE = MAX_TAB_SIZE;

    /** Ace Editor Options **/
    editorMode: string; // string or mode object
    isLoading = false;
    annotationsArray: Array<Annotation> = [];
    annotationChange: Subscription;
    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};
    // Inline feedback variables
    fileFeedbacks: Feedback[];
    lineCounter: any[] = [];
    private elementArray: Element[] = [];
    fileFeedbackPerLine: { [line: number]: Feedback } = {};
    editorSession: any;
    markerIds: number[] = [];
    gutterHighlights: Map<number, string[]> = new Map<number, string[]>();
    tabSize = 4;

    // Icons
    farFileAlt = faFileAlt;
    faPlusSquare = faPlusSquare;
    faCircleNotch = faCircleNotch;
    faGear = faGear;

    constructor(private repositoryFileService: CodeEditorRepositoryFileService, private fileService: CodeEditorFileService, protected localStorageService: LocalStorageService) {}

    /**
     * @function ngAfterViewInit
     * @desc Sets the theme and other editor options
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
    }

    /**
     * @function ngOnChanges
     * @desc New clean state       => reset the editor and file update subscriptions
     *       New selectedFile      => load the file from the repository and open it in the editor
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
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
            // Current file has changed
            // Only load the file from server if there is nothing stored in the editorFileSessions
            if (this.selectedFile && !this.fileSession[this.selectedFile]) {
                this.loadFile(this.selectedFile);
            } else {
                this.initEditorAfterFileChange();
            }
        }
        if (changes.commitState && changes.commitState.currentValue === CommitState.CONFLICT) {
            this.editor.setReadOnly(true);
        } else if (changes.commitState && changes.commitState.previousValue === CommitState.CONFLICT && changes.commitState.currentValue !== CommitState.CONFLICT) {
            this.editor.setReadOnly(false);
        }
    }

    /**
     * Setup the ace editor after a file change occurred.
     * Makes sure previous settings are restored and the correct language service is used.
     **/
    initEditorAfterFileChange() {
        // Setup editorSession for inline feedback using lineWidgets
        this.editorSession = this.editor.getEditor().getSession();

        if (!this.editorSession.widgetManager) {
            this.editorSession.widgetManager = new this.LineWidgets(this.editorSession);
            this.editorSession.widgetManager.attach(this.editor.getEditor());
        }
        // Remove previous lineWidgets
        if (this.editorSession.lineWidgets) {
            this.editorSession.lineWidgets.forEach((widget: any) => {
                if (widget) {
                    this.editorSession.widgetManager.removeLineWidget(widget);
                }
            });
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
            // Reset the undo stack after file change, otherwise the user can undo back to the old file
            this.editor.getEditor().getSession().setUndoManager(new UndoManager());
            this.displayAnnotations();

            // Setup inline feedbacks
            // Get amount of lines of code in order to render for each line a corresponding inline feedback component
            if (this.isTutorAssessment) {
                const lines = this.editor.getEditor().getSession().getLength();
                this.lineCounter = new Array(lines);
                if (!this.feedbacks) {
                    this.feedbacks = [];
                }
                this.fileFeedbacks = this.feedbacks.filter((feedback) => feedback.reference && feedback.reference.includes(this.selectedFile));
                this.fileFeedbackPerLine = {};
                this.fileFeedbacks.forEach((feedback) => {
                    const line: number = +feedback.reference!.split('line:')[1];
                    this.fileFeedbackPerLine[line] = feedback;
                });
            }

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

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor.
     * Is used for updating the error annotations in the editor and giving the touched file the unsaved flag.
     * @param event {string} Current editor code
     */
    onFileTextChanged(event: any) {
        const code = event as string;
        if (this.isTutorAssessment) {
            this.editor.setReadOnly(true);
            if (!this.readOnlyManualFeedback) {
                this.setupLineIcons();
            }
            this.displayFeedbacks();
        }
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            if (this.fileSession[this.selectedFile].code !== code) {
                const cursor = this.editor.getEditor().getCursorPosition();
                this.fileSession[this.selectedFile] = { code, cursor };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: code });
            }
        }
    }

    ngOnDestroy() {
        if (this.annotationChange) {
            this.annotationChange.unsubscribe();
        }
    }

    /**
     * Recalculates the position of annotations according to changes in the editor.
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
     * Sets the annotations for the editor.
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
    onFileChange(fileChange: FileChange) {
        if (fileChange instanceof RenameFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            this.annotationsArray = this.annotationsArray.map((a) =>
                a.fileName !== fileChange.oldFileName
                    ? a
                    : {
                          ...a,
                          fileName: fileChange.newFileName,
                      },
            );
            this.storeAnnotations([fileChange.newFileName]);
        } else if (fileChange instanceof DeleteFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            this.annotationsArray = this.annotationsArray.filter((a) => a.fileName === fileChange.fileName);
            this.storeAnnotations([fileChange.fileName]);
        } else if (fileChange instanceof CreateFileChange && this.selectedFile === fileChange.fileName) {
            this.fileSession = { ...this.fileSession, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 } } };
            this.initEditorAfterFileChange();
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
     * Displays the inline feedback of a line of code using lineWidgets. We first go through all feedbacks of the selected file
     * and create a lineWidget for each feedback. The elementArray contains all inline feedback components which have been added as lineWidget.
     */
    displayFeedbacks() {
        this.fileFeedbacks.forEach((feedback) => {
            const line: number = +feedback.reference!.split('line:')[1];
            this.addLineWidgetWithFeedback(line);
        });
    }

    /**
     * Adds the inline comment button to all visible gutters in the ace editor.
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
                clone.addEventListener('click', () => this.addLineWidgetWithFeedback(+gutter.innerText - 1));
                // TODO: Check whether this causes an issue when having annotations
                if (gutter.childElementCount < 1) {
                    gutter.appendChild(clone);
                }
            });
        });
    }

    /**
     * Add lineWidget for specific line of code.
     * @param line line of code where the feedback inline component will be added to.
     */
    addLineWidgetWithFeedback(line: number) {
        // If the component was not found in the elementArray, we get it from the DOM and add it to elementArray
        let inlineFeedback: Element | null = this.elementArray.find((element) => element.id === 'test-' + line) ?? null;
        if (!inlineFeedback) {
            inlineFeedback = document.querySelector(`#test-${line}`);
            if (inlineFeedback) {
                this.elementArray.push(inlineFeedback);
            }
        }
        if (inlineFeedback) {
            const lineWidget = {
                row: line,
                fixedWidth: true,
                coverGutter: true,
                el: inlineFeedback,
            };
            // Check if lineWidget is already displayed
            if (this.editorSession.lineWidgets) {
                const displayedWidget = this.editorSession.lineWidgets.find((w: any) => w && w.row === lineWidget.row);
                if (!displayedWidget) {
                    lineWidget.el.className = 'inline-feedback';
                    this.editorSession.widgetManager.addLineWidget(lineWidget);
                }
            } else {
                lineWidget.el.className = 'inline-feedback';
                this.editorSession.widgetManager.addLineWidget(lineWidget);
            }
        }
    }

    /**
     * Adjusts the height of the ace editor after the lineWidget (inline feedback component) changed size
     * @param line Line of code which has inline feedback (lineWidget)
     */
    adjustLineWidgetHeight(line: number) {
        const widget = this.editorSession.lineWidgets.find((w: any) => w && w.el?.id === 'test-' + line);
        this.editorSession.widgetManager.removeLineWidget(widget);
        this.editorSession.widgetManager.addLineWidget(widget);
    }

    /**
     * Called whenever an inline feedback element is emitted. Updates existing feedbacks or adds onto it
     * @param feedback Newly created inline feedback.
     */
    updateFeedback(feedback: Feedback) {
        const line: number = +feedback.reference!.split('line:')[1];
        // Check if feedback already exists and update it, else append it to feedbacks of the file
        if (this.feedbacks.some((f) => f.reference === feedback.reference)) {
            const index = this.feedbacks.findIndex((f) => f.reference === feedback.reference);
            this.feedbacks[index] = feedback;
            this.fileFeedbackPerLine[line] = feedback;
        } else {
            this.feedbacks.push(feedback);
            this.fileFeedbackPerLine[line] = feedback;
        }
        this.onUpdateFeedback.emit(this.feedbacks);
        this.adjustLineWidgetHeight(line);
    }

    /**
     * Called whenever an inline feedback is cancelled. Removes it from ace editor or just aligns height.
     * @param line
     */
    cancelFeedback(line: number) {
        if (!this.fileFeedbackPerLine[line]) {
            const widget = this.editorSession.lineWidgets.filter((w: any) => w && w.el?.id === 'test-' + line)[0];
            this.editorSession.widgetManager.removeLineWidget(widget);
        } else {
            this.adjustLineWidgetHeight(line);
        }
    }

    /**
     * Deletes a feedback from the feedbacks
     * @param feedback Feedback to be removed
     */
    deleteFeedback(feedback: Feedback) {
        const indexToDelete = this.feedbacks.indexOf(feedback);
        const line: number = +feedback.reference!.split('line:')[1];
        this.feedbacks.splice(indexToDelete, 1);
        delete this.fileFeedbackPerLine[line];
        this.cancelFeedback(line);
        this.onUpdateFeedback.emit(this.feedbacks);
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

    /**
     * Changes the tab size to a valid value in case it is not.
     *
     * Valid values are in range [1, {@link MAX_TAB_SIZE}].
     */
    validateTabSize(): void {
        this.tabSize = Math.max(1, Math.min(this.tabSize, MAX_TAB_SIZE));
    }
}
