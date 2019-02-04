import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Participation } from '../../entities/participation';
import { RepositoryFileService } from '../../entities/repository/repository.service';
import { WindowRef } from '../../core/websocket/window.service';
import { JhiAlertService } from 'ng-jhipster';
import { JhiWebsocketService } from '../../core';
import { EditorComponent } from '../editor.component';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/dreamweaver';
import 'brace/ext/modelist';
import 'brace/mode/java';
import 'brace/mode/javascript';
import 'brace/mode/markdown';
// TODO: consider adding any modes we might need

declare let ace: any;

@Component({
    selector: 'jhi-editor-ace',
    templateUrl: './editor-ace.component.html',
    providers: [JhiAlertService, WindowRef, NgbModal, RepositoryFileService]
})
export class EditorAceComponent implements OnInit, AfterViewInit, OnChanges {
    @ViewChild('editor')
    editor: AceEditorComponent;

    /** Ace Editor Options **/
    editorText = '';
    editorFileSessions: object = {};
    editorMode = 'java'; // String or mode object
    editorReadOnly = false;
    editorAutoUpdate = true; // change content when editor text changes
    editorDurationBeforeCallback = 800; // wait 0,8s before callback 'textChanged' sends new value

    /** Callback timing variables **/
    updateFilesDebounceTime = 3000;
    saveFileDelayTime = 2500;

    @Input()
    participation: Participation;
    @Input()
    fileName: string;
    @Output()
    saveStatusChange = new EventEmitter<object>();

    constructor(
        private parent: EditorComponent,
        private jhiWebsocketService: JhiWebsocketService,
        private repositoryFileService: RepositoryFileService,
        public modalService: NgbModal
    ) {}

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
        ace.acequire('ace/ext/language_tools');
        this.editor.setTheme('dreamweaver');
        this.editor.getEditor().setOptions({
            animatedScroll: true
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
        }
        if (changes.fileName && this.fileName) {
            // Current file has changed
            this.loadFile(this.fileName);
        }
    }

    onSaveStatusChange(statusChange: object) {
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
        // This fetches a list of all supported editor modes and matches it afterwards against the file extension
        const aceModeList = ace.acequire('ace/ext/modelist');
        const fileNameSplit = fileName ? fileName.split('/') : '';
        const aceMode = aceModeList.getModeForPath(fileNameSplit[fileNameSplit.length - 1]);

        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.get(this.participation.id, fileName).subscribe(
            fileObj => {
                if (!this.editorFileSessions[fileName]) {
                    this.editorFileSessions[fileName] = {};
                    this.editorFileSessions[fileName].code = fileObj.fileContent;
                    this.editorFileSessions[fileName].fileName = fileName;
                }
                /**
                 * Assign the obtained file content to the editor and set the ace mode
                 * Additionally, we resize the editor window and set focus to it
                 */
                this.editorText = fileObj.fileContent;
                this.editor.setMode(aceMode);
                this.editor.getEditor().resize();
                this.editor._editor.focus();
            },
            err => {
                console.log('There was an error while getting file', this.fileName, err);
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

            this.repositoryFileService
                .update(this.participation.id, fileName, this.editorFileSessions[fileName].code)
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
                        console.log('There was an error while saving file', this.fileName, err);
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
        if (this.editorFileSessions[this.fileName] && this.editorFileSessions[this.fileName].code !== code) {
            // Assign received code to our session
            this.editorFileSessions[this.fileName].code = code;
            this.editorFileSessions[this.fileName].unsavedChanges = true;

            // Trigger file save
            this.saveFile(this.fileName);
            this.updateSaveStatusLabel();
        }
    }
}
