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
import { LocalStorageService } from 'ngx-webstorage';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { difference as _difference } from 'lodash';
import { fromEvent, Subscription } from 'rxjs';

import { hasParticipationChanged, Participation } from 'app/entities/participation';
import { RepositoryFileService } from 'app/entities/repository';
import { WindowRef } from 'app/core';
import * as ace from 'brace';

import { TextChange, AnnotationArray } from '../../entities/ace-editor';
import { JhiWebsocketService } from '../../core';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';
import { RenameFileChange, FileChange, DeleteFileChange } from 'app/entities/ace-editor/file-change.model';

@Component({
    selector: 'jhi-code-editor-ace',
    templateUrl: './code-editor-ace.component.html',
    providers: [JhiAlertService, WindowRef, NgbModal, RepositoryFileService],
})
export class CodeEditorAceComponent implements AfterViewInit, OnChanges, OnDestroy {
    @ViewChild('editor')
    editor: AceEditorComponent;

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = ace.acequire('ace/ext/modelist');

    /** Ace Editor Options **/
    editorMode = this.aceModeList.getModeForPath('Test.java').name; // String or mode object

    annotationChange: Subscription;

    @Input()
    participation: Participation;
    @Input()
    selectedFile: string;
    @Input()
    fileChange: FileChange;
    @Input()
    buildLogErrors: { [fileName: string]: AnnotationArray };
    @Input()
    readonly unsavedFiles: string[];
    @Output()
    onEditorStateChange = new EventEmitter<EditorState>();
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    onSavedFiles = new EventEmitter<string[]>();
    @Output()
    onFileContentChange = new EventEmitter<{ file: string; unsavedChanges: boolean }>();

    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};
    // We store changes in the editor since the last content emit to update annotation positions.
    editorChangeLog: TextChange[] = [];

    isLoading = false;

    updateUnsavedFilesChannel: string;
    receiveFileUpdatesChannel: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private repositoryFileService: RepositoryFileService,
        private localStorageService: LocalStorageService,
        public modalService: NgbModal,
    ) {}

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
     *       New selectedFile      => load the file from the repository and open it in the editor
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.updateUnsavedFilesChannel = `/topic/repository/${this.participation.id}/files`;
            this.receiveFileUpdatesChannel = `/user${this.updateUnsavedFilesChannel}`;
            this.setUpReceiveFileUpdates();
        }
        if (changes.fileChange && changes.fileChange.currentValue) {
            if (this.fileChange instanceof RenameFileChange) {
                const fileSessionContent = this.fileSession[this.fileChange.oldFileName];
                delete this.fileSession[this.fileChange.oldFileName];
                this.fileSession[this.fileChange.newFileName] = fileSessionContent;
            } else if (this.fileChange instanceof DeleteFileChange) {
                delete this.fileSession[this.fileChange.fileName];
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
        } else if (changes.buildLogErrors && changes.buildLogErrors.currentValue) {
            this.editor
                .getEditor()
                .getSession()
                .setAnnotations(this.buildLogErrors[this.selectedFile]);
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
        this.editor
            .getEditor()
            .getSession()
            .setAnnotations(this.buildLogErrors[this.selectedFile]);
    }

    /**
     * Store the error data in the localStorage (synchronous action).
     */
    storeSession() {
        const sessionAnnotations = this.buildLogErrors.errors;
        this.localStorageService.store('sessions', JSON.stringify({ [this.participation.id]: { errors: sessionAnnotations, timestamp: Date.now() } }));
    }

    /**
     * Set up the websocket for retrieving the result of attempted file updates.
     * Checks which files could be updated within the file submission and updates the editor state accordingly.
     * All files could be updated -> clean / Some files could not be updated -> unsaved changes.
     */
    setUpReceiveFileUpdates() {
        this.jhiWebsocketService.unsubscribe(this.receiveFileUpdatesChannel);
        this.jhiWebsocketService.subscribe(this.receiveFileUpdatesChannel);
        this.jhiWebsocketService
            .receive(this.receiveFileUpdatesChannel)
            .pipe(
                debounceTime(500),
                distinctUntilChanged(),
            )
            .subscribe(
                res => {
                    this.storeSession();
                    this.onSavedFiles.emit(res);
                },
                err => {
                    this.onError.emit(err.error);
                    this.onEditorStateChange.emit(EditorState.UNSAVED_CHANGES);
                },
            );
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFile(fileName: string) {
        this.isLoading = true;
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.get(this.participation.id, fileName).subscribe(
            fileObj => {
                this.fileSession[fileName] = { code: fileObj.fileContent, cursor: { column: 0, row: 0 } };
                this.isLoading = false;
                setTimeout(() => this.initEditorAfterFileChange(), 0);
            },
            err => {
                console.log('There was an error while getting file', this.selectedFile, err);
            },
        );
    }

    /**
     * @function saveFiles
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles() {
        if (this.unsavedFiles.length) {
            this.onEditorStateChange.emit(EditorState.SAVING);
            this.jhiWebsocketService.send(this.updateUnsavedFilesChannel, this.unsavedFiles.map(fileName => ({ fileName, fileContent: this.fileSession[fileName].code })));
        }
    }

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor.
     * Is used for updating the error annotations in the editor and giving the touched file the unsaved flag.
     * @param code {string} Current editor code
     */
    onFileTextChanged(code: string) {
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.fileSession[this.selectedFile].code !== code) {
            const cursor = this.editor.getEditor().getCursorPosition();
            this.fileSession[this.selectedFile] = { code, cursor };
            this.buildLogErrors = {
                ...this.buildLogErrors,
                [this.selectedFile]: this.editorChangeLog.reduce((errors, change) => errors.update(change), this.buildLogErrors[this.selectedFile]),
            };
            this.editorChangeLog = [];
            this.onFileContentChange.emit({ file: this.selectedFile, unsavedChanges: true });
        }
    }

    ngOnDestroy() {
        if (this.annotationChange) {
            this.annotationChange.unsubscribe();
        }
        if (this.updateUnsavedFilesChannel) {
            this.jhiWebsocketService.unsubscribe(this.updateUnsavedFilesChannel);
        }
    }
}
