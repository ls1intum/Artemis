import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/javascript';
import 'brace/mode/markdown';
import 'brace/mode/python';
import 'brace/theme/dreamweaver';

import { AceEditorComponent } from 'ng2-ace-editor';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { fromEvent, Subscription } from 'rxjs';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';

import { RepositoryFileService } from 'app/entities/repository';
import { WindowRef } from 'app/core';
import * as ace from 'brace';

import { AnnotationArray, TextChange } from 'app/entities/ace-editor';
import { DeleteFileChange, FileChange, RenameFileChange } from 'app/entities/ace-editor/file-change.model';
import { CodeEditorRepositoryFileService } from 'app/code-editor/service';
import { CommitState } from 'app/code-editor/model';

@Component({
    selector: 'jhi-code-editor-ace',
    templateUrl: './code-editor-ace.component.html',
    providers: [JhiAlertService, WindowRef, NgbModal, RepositoryFileService],
})
export class CodeEditorAceComponent implements AfterViewInit, OnChanges, OnDestroy {
    @ViewChild('editor')
    editor: AceEditorComponent;

    @Input()
    readonly selectedFile: string;
    @Input()
    readonly fileChange: FileChange;
    @Input()
    readonly commitState: CommitState;
    @Input()
    get buildLogErrors(): { errors: { [fileName: string]: AnnotationArray }; timestamp: number } {
        return this.buildLogErrorsValue;
    }
    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();
    @Output()
    buildLogErrorsChange = new EventEmitter<{ errors: { [fileName: string]: AnnotationArray }; timestamp: number }>();
    @Output()
    onError = new EventEmitter<string>();

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = ace.acequire('ace/ext/modelist');
    /** Ace Editor Options **/
    // TODO: Is the hardcoded string a bug?
    editorMode = this.aceModeList.getModeForPath('Test.java').name; // String or mode object
    isLoading = false;
    annotationChange: Subscription;
    buildLogErrorsValue: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };
    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};
    // We store changes in the editor since the last content emit to update annotation positions.
    editorChangeLog: TextChange[] = [];

    set buildLogErrors(buildLogErrors) {
        this.buildLogErrorsValue = buildLogErrors;
        this.buildLogErrorsChange.emit(this.buildLogErrors);
    }

    constructor(public modalService: NgbModal, private repositoryFileService: CodeEditorRepositoryFileService) {}

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
    }

    /**
     * @function ngOnChanges
     * @desc New participation     => reset the file update subscriptions
     *       File has happened     => update internal variables to reflect change
     *       New selectedFile      => load the file from the repository and open it in the editor
     *       New buildLogErrors    => update the ui with the annotations
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.commitState && changes.commitState.previousValue !== CommitState.UNDEFINED && this.commitState === CommitState.UNDEFINED) {
            this.fileSession = {};
            this.editor
                .getEditor()
                .getSession()
                .setValue('');
        }
        if (changes.fileChange && changes.fileChange.currentValue) {
            if (this.fileChange instanceof RenameFileChange) {
                // Rename references to file / path
                const { oldFileName, newFileName } = this.fileChange;
                const oldFileNameRegex = new RegExp(`^${oldFileName}`);
                const renamedSessions = compose(
                    fromPairs,
                    map(([fileName, session]) => [fileName.replace(oldFileNameRegex, newFileName), session]),
                    toPairs,
                )(this.fileSession);
                const filteredSession = compose(
                    fromPairs,
                    filter(([fileName]) => fileName !== oldFileName),
                    toPairs,
                )(this.fileSession);
                this.fileSession = { ...filteredSession, ...renamedSessions };
            } else if (this.fileChange instanceof DeleteFileChange) {
                // Make sure to also remove references to sub items (files in folder)
                const { fileName } = this.fileChange;
                this.fileSession = compose(
                    fromPairs,
                    filter(([fn]) => !fn.startsWith(fileName)),
                    toPairs,
                )(this.fileSession);
            }
        }
        // Current file has changed
        if (changes.selectedFile && this.selectedFile) {
            // Only load the file from server if there is nothing stored in the editorFileSessions
            if (!this.fileSession[this.selectedFile]) {
                this.loadFile(this.selectedFile);
            } else {
                this.initEditorAfterFileChange();
            }
        }
        // Build log errors have changed - this can be new build results, but also a file change that has updated the object
        if (changes.buildLogErrors && changes.buildLogErrors.currentValue) {
            this.editor
                .getEditor()
                .getSession()
                .setAnnotations(this.buildLogErrors.errors[this.selectedFile]);
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
        this.editor
            .getEditor()
            .getSession()
            .setValue(this.fileSession[this.selectedFile].code);
        this.annotationChange = fromEvent(this.editor.getEditor().getSession(), 'change').subscribe(([change]) => {
            this.editorChangeLog.push(change);
        });

        // Restore the previous cursor position
        this.editor.getEditor().moveCursorToPosition(this.fileSession[this.selectedFile].cursor);
        this.editorMode = this.aceModeList.getModeForPath(this.selectedFile).name;
        this.editor.setMode(this.editorMode);
        this.editor.getEditor().resize();
        this.editor.getEditor().focus();
        // Reset the undo stack after file change, otherwise the user can undo back to the old file
        this.editor
            .getEditor()
            .getSession()
            .setUndoManager(new ace.UndoManager());
        if (this.buildLogErrors) {
            this.editor
                .getEditor()
                .getSession()
                .setAnnotations(this.buildLogErrors.errors[this.selectedFile]);
        }
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFile(fileName: string) {
        this.isLoading = true;
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.getFile(fileName).subscribe(
            fileObj => {
                this.fileSession[fileName] = { code: fileObj.fileContent, cursor: { column: 0, row: 0 } };
                this.isLoading = false;
                this.initEditorAfterFileChange();
            },
            err => {
                console.log('There was an error while getting file', this.selectedFile, err);
            },
        );
    }

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor.
     * Is used for updating the error annotations in the editor and giving the touched file the unsaved flag.
     * @param code {string} Current editor code
     */
    onFileTextChanged(code: string) {
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.selectedFile && this.fileSession[this.selectedFile].code !== code) {
            const cursor = this.editor.getEditor().getCursorPosition();
            this.fileSession[this.selectedFile] = { code, cursor };
            if (this.buildLogErrors.errors[this.selectedFile]) {
                this.buildLogErrors = {
                    ...this.buildLogErrors,
                    errors: {
                        ...this.buildLogErrors.errors,
                        [this.selectedFile]: this.editorChangeLog.reduce((errors, change) => errors.update(change), this.buildLogErrors.errors[this.selectedFile]),
                    },
                };
            }
            this.editorChangeLog = [];
            this.onFileContentChange.emit({ file: this.selectedFile, fileContent: code });
        }
    }

    ngOnDestroy() {
        if (this.annotationChange) {
            this.annotationChange.unsubscribe();
        }
    }
}
