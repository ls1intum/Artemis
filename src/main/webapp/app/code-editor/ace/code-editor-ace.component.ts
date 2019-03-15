import 'brace/ext/language_tools';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/javascript';
import 'brace/mode/markdown';
import 'brace/mode/python';
import 'brace/theme/dreamweaver';

import { AceEditorComponent } from 'ng2-ace-editor';
import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { isEmpty as _isEmpty, union as _union } from 'lodash';
import { fromEvent, Subscription } from 'rxjs';

import { Participation } from 'app/entities/participation';
import { RepositoryFileService } from 'app/entities/repository';
import { WindowRef } from 'app/core';
import * as ace from 'brace';

import { AceAnnotation, SaveStatusChange } from '../../entities/ace-editor';

@Component({
    selector: 'jhi-code-editor-ace',
    templateUrl: './code-editor-ace.component.html',
    providers: [JhiAlertService, WindowRef, NgbModal, RepositoryFileService]
})
export class CodeEditorAceComponent implements OnInit, AfterViewInit, OnChanges {
    @ViewChild('editor')
    editor: AceEditorComponent;

    // This fetches a list of all supported editor modes and matches it afterwards against the file extension
    readonly aceModeList = ace.acequire('ace/ext/modelist');

    /** Ace Editor Options **/
    editorFileSessions: {[fileName: string]: {code: string, errors: AceAnnotation[], unsavedChanges: boolean}} = {};
    editorMode = this.aceModeList.getModeForPath('Test.java').name; // String or mode object

    annotationChange: Subscription;

    /** Callback timing variables **/
    updateFilesDebounceTime = 3000;
    saveFileDelayTime = 2500;

    @Input()
    participation: Participation;
    @Input()
    selectedFile: string;
    @Input()
    session: any;
    @Input()
    buildLogErrors: {[fileName: string]: AceAnnotation[]};
    @Output()
    saveStatusChange = new EventEmitter<SaveStatusChange>();

    constructor(
        private repositoryFileService: RepositoryFileService,
        public modalService: NgbModal
    ) {
    }

    /**
     * @function ngOnInit
     * @desc Initially sets the labels for file save status
     */
    ngOnInit(): void {
        this.updateSaveStatusLabel();
    }

    /**
     * @function ngAfterViewInit
     * @desc Sets the theme and other editor options
     */
    ngAfterViewInit(): void {
        this.editor.setTheme('dreamweaver');
        this.editor.getEditor().setOptions({
            animatedScroll: true,
            enableBasicAutocompletion: true,
            enableLiveAutocompletion: true
        });
    }

    /**
     * @function ngOnChanges
     * @desc New participation => update the file save status labels
     *       New fileName      => load the file from the repository and open it in the editor
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.participation && this.participation) {
            this.updateSaveStatusLabel();
            this.loadSession();
        }
        // Current file has changed
        if (changes.selectedFile && this.selectedFile) {
            if (this.annotationChange) {
                // Unsubscribe, otherwise the event of changing the whole text will be received
                this.annotationChange.unsubscribe();
                this.editor.getEditor().getSession().off('change', this.recalculateAnnotationPositions);
            }
            this.loadFile(this.selectedFile);
        // If both a session file and buildLogErrors exists, decide by time which are newer
        } else if (this.session && changes.buildLogErrors) {
            const buildLogEntry = changes.buildLogErrors.currentValue && Object.values(changes.buildLogErrors.currentValue).flat().find(({ts}: any) => ts);
            if (!_isEmpty(this.session) && (!buildLogEntry || buildLogEntry.ts < this.session.ts)) {
                const fileNames = _union(Object.keys(this.session.errors), Object.keys(this.editorFileSessions));
                this.editorFileSessions = fileNames 
                    .reduce((acc, fileName) => ({
                        ...acc, [fileName]: {...this.editorFileSessions[fileName], errors: this.session.errors[fileName]}
                    }), {});
            } else {
                const fileNames = _union(Object.keys(this.buildLogErrors), Object.keys(this.editorFileSessions));
                this.editorFileSessions = fileNames 
                    .reduce((acc, fileName) => ({
                        ...acc, [fileName]: {...this.editorFileSessions[fileName], errors: changes.buildLogErrors.currentValue[fileName] ? changes.buildLogErrors.currentValue[fileName].map(({ts, ...rest}) => rest) : []}
                }), {});
            }
            this.editor.getEditor().getSession()
                .setAnnotations(this.editorFileSessions[this.selectedFile] ? this.editorFileSessions[this.selectedFile].errors : []);
        }
    }

    /**
     * @function loadReadme
     * @desc Gets the README.md file from our repository and starts the rendering process
     */
    loadSession() {
        // Only do this if we already received a participation object from parent
        if (this.participation) {
            this.repositoryFileService.get(this.participation.id, 'session.json').subscribe(
                fileObj => {
                        this.session = JSON.parse(fileObj.fileContent || '{}');
                },
                err => {
                    // TODO: handle the case that there is no README.md file
                    console.log('Error while getting session.json file!', err);
                }
            );
        }
    }

    recalculateAnnotationPositions = (change: any) => {
        const {start: {row: rowStart, column: columnStart}, end: {row: rowEnd, column: columnEnd}, action} = change;
        // TODO: Hotfix for issue with change listener
        if (this.editorFileSessions[this.selectedFile]) {
            if (action === 'remove' || action === 'insert') {
                const sign = action === 'remove' ? -1 : 1;
                const updateRowDiff = sign * (rowEnd - rowStart);
                const updateColDiff = sign * (columnEnd - columnStart);
                const updatedAnnotations = this.editorFileSessions[this.selectedFile].errors
                    .map(({row, column, ...rest}) => {
                        return {
                            ...rest,
                            row: row >= rowStart ? row + updateRowDiff : row,
                            column: column >= columnStart ? column + updateColDiff : column
                        };
                    });
                this.editorFileSessions[this.selectedFile].errors = updatedAnnotations;
            }
        }
    }

    onSaveStatusChange(statusChange: SaveStatusChange) {
        this.saveStatusChange.emit(statusChange);
    }

    /**
     * @function updateSaveStatusLabel
     * @desc Sets the labels under the ngx-treeview (files) according to the status of the files
     */
    updateSaveStatusLabel() {
        const sessionKeys = Object.keys(this.editorFileSessions);
        const unsavedFiles = sessionKeys.filter(session => this.editorFileSessions[session].unsavedChanges === true).length;
        if (unsavedFiles > 0) {
            this.onSaveStatusChange({
                isSaved: false,
                saveStatusIcon: {
                  spin: true,
                  icon: 'circle-notch',
                  class: 'text-info'
                },
                saveStatusLabel:
                    `<span class="text-info">Unsaved changes in ${unsavedFiles} files.</span>`
            });
        } else {
            this.onSaveStatusChange({
                isSaved: true,
                saveStatusIcon: {
                    spin: false,
                    icon: 'check-circle',
                    class: 'text-success'
                },
                saveStatusLabel: '<span class="text-success"> All changes saved.</span>'
            });
        }
    }

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
                        errors: [],
                        unsavedChanges: false
                    };
                } else {
                    this.editorFileSessions[fileName] = {
                        ...this.editorFileSessions[fileName],
                        code: fileObj.fileContent,
                        unsavedChanges: false
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
            },
            err => {
                console.log('There was an error while getting file', this.selectedFile, err);
            }
        );
    }

    /**
     * @function saveFile
     * @desc Saves the currently selected file; is being called when the file is changed (onFileChanged)
     * @param fileName: name of currently selected file
     */
    saveFile(fileName: string) {
        // Delay file save
        setTimeout(() => {
            this.onSaveStatusChange({
                isSaved: false,
                saveStatusIcon: {
                    spin: true,
                    icon: 'circle-notch',
                    class: 'text-info'
                },
                saveStatusLabel: '<span class="text-info">Saving file.</span>'
            });

            const sessionAnnotations = Object.entries(this.editorFileSessions).reduce((acc, [file, {errors}]) => ({
                ...acc,
                [file]: errors
            }), {});

            this.repositoryFileService
                .update(this.participation.id, fileName, this.editorFileSessions[fileName].code, sessionAnnotations)
                .debounceTime(this.updateFilesDebounceTime)
                .distinctUntilChanged()
                .subscribe(
                    () => {
                        this.editorFileSessions[fileName].unsavedChanges = false;
                        this.updateSaveStatusLabel();
                    },
                    err => {
                        if (this.onSaveStatusChange) {
                            this.onSaveStatusChange({
                                isSaved: false,
                                saveStatusIcon: {
                                    spin: false,
                                    icon: 'times-circle',
                                    class: 'text-danger'
                                },
                                saveStatusLabel: '<span class="text-danger"> Failed to save file.</span>'
                            });
                        }
                        console.log('There was an error while saving file', this.selectedFile, err);
                    }
                );
        }, this.saveFileDelayTime);
    }

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor
     * @param code {string} Current editor code
     */
    onFileTextChanged(code: string) {
        /** Is the code different to what we have on our session? This prevents us from saving when a file is loaded **/
        if (this.editorFileSessions[this.selectedFile] && this.editorFileSessions[this.selectedFile].code !== code) {
            // Assign received code to our session
            this.editorFileSessions[this.selectedFile] = {
                ...this.editorFileSessions[this.selectedFile],
                code,
                unsavedChanges: true
            };

            // Trigger file save
            this.saveFile(this.selectedFile);
            this.updateSaveStatusLabel();
        // On initial change set annotations and subscribe to changes
        } else if (this.editorFileSessions[this.selectedFile]) {
            this.editor.getEditor().getSession()
                .setAnnotations(this.editorFileSessions[this.selectedFile].errors);
            this.annotationChange = fromEvent(this.editor.getEditor().getSession(), 'change')
                .subscribe(([change]) => this.recalculateAnnotationPositions(change));
        }
    }
}
