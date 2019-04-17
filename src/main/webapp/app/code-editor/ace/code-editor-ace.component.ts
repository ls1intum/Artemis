import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/javascript';
import 'brace/mode/markdown';
import 'brace/mode/python';
import 'brace/theme/dreamweaver';

import { AceEditorComponent } from 'ng2-ace-editor';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild, OnDestroy } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { difference as _difference, differenceWith as _differenceWith } from 'lodash';
import { compose, fromPairs, map, toPairs, unionBy } from 'lodash/fp';
import { fromEvent, Subscription } from 'rxjs';

import { hasParticipationChanged, Participation } from 'app/entities/participation';
import { RepositoryFileService } from 'app/entities/repository';
import { WindowRef } from 'app/core';
import * as ace from 'brace';

import { AnnotationArray, TextChange } from '../../entities/ace-editor';
import { JhiWebsocketService } from '../../core';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

export enum EditorState {
    CLEAN = 'CLEAN',
    UNSAVED_CHANGES = 'UNSAVED_CHANGES',
    SAVING = 'SAVING',
}

@Component({
    selector: 'jhi-code-editor-ace',
    templateUrl: './code-editor-ace.component.html',
    providers: [JhiAlertService, WindowRef, NgbModal, RepositoryFileService],
})
export class CodeEditorAceComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
    @ViewChild('editor')
    editor: AceEditorComponent;

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = ace.acequire('ace/ext/modelist');

    /** Ace Editor Options **/
    editorFileSessions: { [fileName: string]: { code: string; errors: AnnotationArray; unsavedChanges: boolean } } = {};
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
    buildLogErrors: { [fileName: string]: AnnotationArray[] };
    @Input()
    editorState: EditorState;
    @Output()
    onEditorStateChange = new EventEmitter<EditorState>();
    @Output()
    onFileSaveStatusChange = new EventEmitter<string[]>();

    updateUnsavedFilesChannel: string;
    receiveFileUpdatesChannel: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private repositoryFileService: RepositoryFileService,
        private localStorageService: LocalStorageService,
        private translate: TranslateService,
        public modalService: NgbModal,
    ) {}

    /**
     * @function ngOnInit
     * @desc Initially sets the labels for file save status
     */
    ngOnInit(): void {}

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
     * @desc New participation => update the file save status labels
     *       New fileName      => load the file from the repository and open it in the editor
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.updateUnsavedFilesChannel = `/topic/repository/${this.participation.id}/files`;
            this.receiveFileUpdatesChannel = `/user${this.updateUnsavedFilesChannel}`;
            this.setUpReceiveFileUpdates();
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
            if (!this.editorFileSessions[this.selectedFile].code) {
                this.loadFile(this.selectedFile);
                // Reset the undo stack after file change, otherwise the user can undo back to the old file
            } else {
                this.editor
                    .getEditor()
                    .getSession()
                    .setValue(this.editorFileSessions[this.selectedFile].code);
                this.editor
                    .getEditor()
                    .getSession()
                    .setUndoManager(new ace.UndoManager());
                this.editor
                    .getEditor()
                    .getSession()
                    .setAnnotations(this.editorFileSessions[this.selectedFile].errors);
                this.annotationChange = fromEvent(this.editor.getEditor().getSession(), 'change').subscribe(([change]) => this.updateAnnotationPositions(change));
            }
        }
        // Update editor file session object to include new files and remove old files
        if (changes.repositoryFiles) {
            const newFiles = _difference(changes.repositoryFiles.currentValue, changes.repositoryFiles.previousValue);
            const removedFiles = _difference(changes.repositoryFiles.previousValue, changes.repositoryFiles.currentValue);
            const filteredEntries = _differenceWith(toPairs(this.editorFileSessions), removedFiles, (a, b) => a === b[0]);
            const newEntries = newFiles.map(fileName => [fileName, { errors: new AnnotationArray(), code: '', unsavedChanges: false }]);
            this.editorFileSessions = compose(
                fromPairs,
                unionBy('[0]', newEntries),
            )(filteredEntries);
        }
        // If there are new errors (through buildLog or a loaded session), overwrite existing errors in the editorFileSessions as they are outdated
        if (changes.buildLogErrors) {
            this.editorFileSessions = compose(
                fromPairs,
                map(([fileName, { errors, ...session }]) => [
                    fileName,
                    {
                        ...session,
                        errors: changes.buildLogErrors.currentValue[fileName] || new AnnotationArray(),
                    },
                ]),
                toPairs,
            )(this.editorFileSessions);
            if (this.editorFileSessions[this.selectedFile]) {
                this.editor
                    .getEditor()
                    .getSession()
                    .setAnnotations(this.editorFileSessions[this.selectedFile].errors);
            }
        }
    }

    setUpReceiveFileUpdates() {
        this.jhiWebsocketService.unsubscribe(this.receiveFileUpdatesChannel);
        this.jhiWebsocketService.subscribe(this.receiveFileUpdatesChannel);
        this.jhiWebsocketService
            .receive(this.receiveFileUpdatesChannel)
            .pipe(
                debounceTime(this.updateFilesDebounceTime),
                distinctUntilChanged(),
            )
            .subscribe(res => {
                const sessionAnnotations = Object.entries(this.editorFileSessions).reduce(
                    (acc, [file, { errors }]) => ({
                        ...acc,
                        [file]: errors,
                    }),
                    {},
                );
                this.localStorageService.store('sessions', JSON.stringify({ [this.participation.id]: { errors: sessionAnnotations, timestamp: Date.now() } }));
                const errors = [];
                const savedFiles: string[] = [];
                Object.entries(res).forEach(([fileName, error]: [string, string | null]) => {
                    if (error) {
                        errors.push(error);
                    } else {
                        this.editorFileSessions[fileName].unsavedChanges = false;
                        savedFiles.push(fileName);
                    }
                });
                this.onFileSaveStatusChange.emit(Object.keys(res).filter(f => !savedFiles.includes(f)));
                if (errors.length) {
                    this.onEditorStateChange.emit(EditorState.UNSAVED_CHANGES);
                } else {
                    this.onEditorStateChange.emit(EditorState.CLEAN);
                }
            });
    }

    /**
     * Update the position of the editor annotations based on the file changes
     * @param change
     */
    updateAnnotationPositions = (change: TextChange) => {
        this.editorFileSessions[this.selectedFile].errors = this.editorFileSessions[this.selectedFile].errors.update(change);
    };

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFile(fileName: string) {
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.get(this.participation.id, fileName).subscribe(
            fileObj => {
                if (!this.editorFileSessions[fileName]) {
                    this.editorFileSessions[fileName] = {
                        code: fileObj.fileContent,
                        errors: new AnnotationArray(),
                        unsavedChanges: false,
                    };
                } else {
                    this.editorFileSessions[fileName] = {
                        ...this.editorFileSessions[fileName],
                        code: fileObj.fileContent,
                        unsavedChanges: false,
                    };
                }
                /**
                 * Assign the obtained file content to the editor and set the ace mode
                 * Additionally, we resize the editor window and set focus to it
                 */
                this.editorMode = this.aceModeList.getModeForPath(fileName).name;
                this.editor.setMode(this.editorMode);
                this.editor.getEditor().resize();
                this.editor.getEditor().focus();
                this.editor.getEditor().setValue(this.editorFileSessions[this.selectedFile].code || '', -1);
                // Reset the undo stack after file change, otherwise the user can undo back to the old file
                this.editor
                    .getEditor()
                    .getSession()
                    .setUndoManager(new ace.UndoManager());
                this.editor
                    .getEditor()
                    .getSession()
                    .setAnnotations(this.editorFileSessions[this.selectedFile].errors);
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
        const unsavedFiles = Object.entries(this.editorFileSessions)
            .filter(([, { unsavedChanges }]) => unsavedChanges)
            .map(([fileName, { code }]) => ({ fileName, fileContent: code }));
        this.onEditorStateChange.emit(EditorState.SAVING);
        this.jhiWebsocketService.send(this.updateUnsavedFilesChannel, unsavedFiles);
    }

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor
     * @param code {string} Current editor code
     */
    onFileTextChanged(code: string) {
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.editorFileSessions[this.selectedFile].code !== code) {
            // Assign received code to our session
            this.editorFileSessions[this.selectedFile] = {
                ...this.editorFileSessions[this.selectedFile],
                code,
                unsavedChanges: true,
            };
            this.onEditorStateChange.emit(EditorState.UNSAVED_CHANGES);
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
