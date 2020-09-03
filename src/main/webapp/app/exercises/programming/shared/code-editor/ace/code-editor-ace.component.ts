import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/markdown';
import 'brace/mode/haskell';
import 'brace/mode/c_cpp';
import 'brace/mode/python';
import 'brace/theme/dreamweaver';
import { AceEditorComponent } from 'ng2-ace-editor';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, ElementRef } from '@angular/core';
import { fromEvent, of, Subscription } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import * as ace from 'brace';
import { CommitState, CreateFileChange, DeleteFileChange, EditorState, FileChange, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { TextChange } from 'app/entities/text-change.model';
import { LocalStorageService } from 'ngx-webstorage';
import { fromPairs, pickBy } from 'lodash';

export type Annotation = { fileName: string; row: number; column: number; text: string; type: string; timestamp: number; hash?: string | null };

@Component({
    selector: 'jhi-code-editor-ace',
    templateUrl: './code-editor-ace.component.html',
    styleUrls: ['./code-editor-ace.scss'],
    providers: [RepositoryFileService],
})
export class CodeEditorAceComponent implements AfterViewInit, OnChanges, OnDestroy {
    @ViewChild('editor', { static: true })
    editor: AceEditorComponent;
    @ViewChild('lineWidgets')
    lineWidgetsElement: { nativeElement: HTMLDivElement };
    @ViewChild('lineIcon')
    lineIconElement: { nativeElement: HTMLDivElement };

    @Input()
    selectedFile: string;
    @Input()
    sessionId: number;
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
    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();
    @Output()
    onError = new EventEmitter<string>();

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = ace.acequire('ace/ext/modelist');
    readonly LineWidgets = ace.acequire('ace/line_widgets').LineWidgets;
    /** Ace Editor Options **/
    editorMode: string; // String or mode object
    isLoading = false;
    annotationsArray: Array<Annotation> = [];
    annotationChange: Subscription;
    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};

    private lineWidget: any;
    private lineWidgetObserver: MutationObserver | null;

    constructor(private repositoryFileService: CodeEditorRepositoryFileService, private fileService: CodeEditorFileService, protected localStorageService: LocalStorageService) {}

    /**
     * @function ngAfterViewInit
     * @desc Sets the theme and other editor options
     */
    ngAfterViewInit(): void {
        this.editor.setTheme('dreamweaver');
        this.editor.getEditor().setOptions({
            animatedScroll: true,
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true,
        });

        this.lineWidgetsElement.nativeElement.remove();
        this.lineIconElement.nativeElement.remove();
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
    }

    /**
     * Setup the ace editor after a file change occurred.
     * Makes sure previous settings are restored and the correct language service is used.
     **/
    initEditorAfterFileChange() {
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
            this.editor.getEditor().getSession().setUndoManager(new ace.UndoManager());
            this.displayAnnotations();
            this.loadLineWidgets();
            this.setupLineIcons();
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
     * @param code {string} Current editor code
     */
    onFileTextChanged(code: string) {
        if (this.isTutorAssessment) {
            this.editor.setReadOnly(true);
        }
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            if (this.fileSession[this.selectedFile].code !== code) {
                const cursor = this.editor.getEditor().getCursorPosition();
                this.fileSession[this.selectedFile] = { code, cursor };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: code });
            }
        }
        this.setupLineIcons();
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
                if (sessionAnnotations[hash] == null || sessionAnnotations[hash].timestamp < a.timestamp) {
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

    loadLineWidgets() {
        const session = this.editor.getEditor().getSession();
        if (!session.widgetManager) {
            session.widgetManager = new this.LineWidgets(session);
            session.widgetManager.attach(this.editor.getEditor());
        }
        if (this.lineWidget) {
            session.widgetManager.removeLineWidget(this.lineWidget);
        }
        if (this.lineWidgetObserver) {
            this.lineWidgetObserver.disconnect();
        }
        const rowElement = this.lineWidgetsElement.nativeElement.children.item(0);
        if (rowElement) {
            this.lineWidgetObserver = new MutationObserver((mutations) => {
                if (mutations.some((m) => m.type === 'attributes' && m.attributeName === 'row')) {
                    this.loadLineWidgets();
                }
            });
            this.lineWidgetObserver.observe(rowElement, { attributes: true });
            const row = rowElement.attributes.getNamedItem('row')?.value || '0';
            this.lineWidget = {
                row: parseInt(row, 10) || 0,
                fixedWidth: true,
                coverGutter: true,
                el: this.lineWidgetsElement,
            };
            this.lineWidgetsElement.nativeElement.remove();
            session.widgetManager.addLineWidget(this.lineWidget);
        }
    }

    setupLineIcons() {
        const container = this.editor.getEditor().container;
        const lines = container.querySelectorAll('.ace_line');
        const gutters = container.querySelectorAll('.ace_gutter-cell');
        lines.forEach((line: HTMLElement, i: number) => {
            line.addEventListener('mouseenter', () => gutters[i].append(this.lineIconElement.nativeElement));
            line.addEventListener('mouseleave', () => this.lineIconElement.nativeElement.remove());
        });
        gutters.forEach((gutter: HTMLElement) => {
            gutter.addEventListener('mouseenter', () => gutter.append(this.lineIconElement.nativeElement));
            gutter.addEventListener('mouseleave', () => this.lineIconElement.nativeElement.remove());
        });
        this.lineIconElement.nativeElement.remove();
    }
}
