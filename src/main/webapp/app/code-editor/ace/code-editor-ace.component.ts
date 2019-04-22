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

import { AnnotationArray, EditorFileSession, TextChange } from '../../entities/ace-editor';
import { JhiWebsocketService } from '../../core';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';

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
    editorFileSession = new EditorFileSession();
    editorMode = this.aceModeList.getModeForPath('Test.java').name; // String or mode object

    annotationChange: Subscription;

    /** Callback timing variables **/
    updateFilesDebounceTime = 500;

    @Input()
    participation: Participation;
    @Input()
    selectedFile: string;
    @Input()
    repositoryFiles: string[];
    @Input()
    buildLogErrors: { [fileName: string]: AnnotationArray };
    @Input()
    editorState: EditorState;
    @Output()
    onEditorStateChange = new EventEmitter<EditorState>();
    @Output()
    onUnsavedFilesChange = new EventEmitter<string[]>();
    @Output()
    onError = new EventEmitter<string>();

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
     *       New repositoryFiles   => update the editorFileSession
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.updateUnsavedFilesChannel = `/topic/repository/${this.participation.id}/files`;
            this.receiveFileUpdatesChannel = `/user${this.updateUnsavedFilesChannel}`;
            this.setUpReceiveFileUpdates();
        }
        // Update editor file session object to include new files and remove old files
        if (changes.repositoryFiles) {
            const newFiles: string[] = _difference(changes.repositoryFiles.currentValue, changes.repositoryFiles.previousValue);
            const removedFiles: string[] = _difference(changes.repositoryFiles.previousValue, changes.repositoryFiles.currentValue);
            this.editorFileSession.update(newFiles, removedFiles);
            const unsavedFiles = this.editorFileSession.getUnsavedFileNames();
            this.onUnsavedFilesChange.emit(unsavedFiles);
        }
        // Current file has changed
        if (changes.selectedFile && this.selectedFile) {
            if (this.annotationChange) {
                // Unsubscribe, otherwise the event of changing the whole text will be received
                if (this.annotationChange) {
                    this.annotationChange.unsubscribe();
                }
                this.editor
                    .getEditor()
                    .getSession()
                    .off('change', this.updateAnnotationPositions);
                this.editor
                    .getEditor()
                    .getSession()
                    .clearAnnotations();
            }
            // Only load the file from server if there is nothing stored in the editorFileSessions
            if (!this.editorFileSession.getCode(this.selectedFile)) {
                this.loadFile(this.selectedFile);
                // Reset the undo stack after file change, otherwise the user can undo back to the old file
            } else {
                this.editor
                    .getEditor()
                    .getSession()
                    .setValue(this.editorFileSession.getCode(this.selectedFile));
                this.editor
                    .getEditor()
                    .getSession()
                    .setUndoManager(new ace.UndoManager());
                this.editor
                    .getEditor()
                    .getSession()
                    .setAnnotations(this.editorFileSession.getErrors(this.selectedFile));
                this.annotationChange = fromEvent(this.editor.getEditor().getSession(), 'change').subscribe(([change]) => this.updateAnnotationPositions(change));
            }
        }
        // If there are new errors (through buildLog or a loaded session), overwrite existing errors in the editorFileSessions as they are outdated
        if ((this.repositoryFiles && changes.buildLogErrors) || (changes.repositoryFiles && this.buildLogErrors)) {
            this.editorFileSession.setErrors(
                ...Object.entries(this.buildLogErrors).map(([fileName, annotations]): [string, AnnotationArray] => [fileName, annotations as AnnotationArray]),
            );
            this.editor
                .getEditor()
                .getSession()
                .setAnnotations(this.editorFileSession.getErrors(this.selectedFile));
        }
    }

    /**
     * Store the error data in the localStorage (synchronous action).
     */
    storeSession() {
        const sessionAnnotations = this.editorFileSession.serialize();
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
                debounceTime(this.updateFilesDebounceTime),
                distinctUntilChanged(),
            )
            .subscribe(
                res => {
                    this.storeSession();

                    const { errorFiles, savedFiles } = Object.entries(res).reduce(
                        (acc, [fileName, error]: [string, string | null]) =>
                            error ? { ...acc, errorFiles: [fileName, ...acc.errorFiles] } : { ...acc, savedFiles: [fileName, ...acc.savedFiles] },
                        { errorFiles: [], savedFiles: [] },
                    );

                    this.editorFileSession.setSaved(...savedFiles);
                    const unsavedFiles = this.editorFileSession.getUnsavedFileNames();
                    this.onUnsavedFilesChange.emit(unsavedFiles);

                    if (errorFiles.length) {
                        this.onError.emit('saveFailed');
                    }
                },
                err => {
                    this.onError.emit(err.error);
                    this.onEditorStateChange.emit(EditorState.UNSAVED_CHANGES);
                },
            );
    }

    /**
     * Update the position of the editor annotations based on the file changes
     * @param change
     */
    updateAnnotationPositions = (change: TextChange) => {
        this.editorFileSession.updateErrorPositions(this.selectedFile, change);
    };

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFile(fileName: string) {
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.get(this.participation.id, fileName).subscribe(
            fileObj => {
                this.editorFileSession.setCode(fileName, fileObj.fileContent);
                /**
                 * Assign the obtained file content to the editor and set the ace mode
                 * Additionally, we resize the editor window and set focus to it
                 */
                this.editorMode = this.aceModeList.getModeForPath(fileName).name;
                this.editor.setMode(this.editorMode);
                this.editor.getEditor().resize();
                this.editor.getEditor().focus();
                this.editor.getEditor().setValue(this.editorFileSession.getCode(fileName) || '', -1);
                // Reset the undo stack after file change, otherwise the user can undo back to the old file
                this.editor
                    .getEditor()
                    .getSession()
                    .setUndoManager(new ace.UndoManager());
                this.editor
                    .getEditor()
                    .getSession()
                    .setAnnotations(this.editorFileSession.getErrors(fileName));
                this.annotationChange = fromEvent(this.editor.getEditor().getSession(), 'change').subscribe(([change]) => this.updateAnnotationPositions(change));
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
        const unsavedFiles = this.editorFileSession.getUnsavedFiles();
        this.onEditorStateChange.emit(EditorState.SAVING);
        this.jhiWebsocketService.send(this.updateUnsavedFilesChannel, unsavedFiles);
    }

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor.
     * Is used for updating the error annotations in the editor and giving the touched file the unsaved flag.
     * @param code {string} Current editor code
     */
    onFileTextChanged(code: string) {
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.editorFileSession.getCode(this.selectedFile) !== code) {
            this.editorFileSession.setCode(this.selectedFile, code);
            this.editorFileSession.setUnsaved(this.selectedFile);
            const unsavedFiles = this.editorFileSession.getUnsavedFileNames();
            this.onUnsavedFilesChange.emit(unsavedFiles);
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
