import {
    Component,
    EventEmitter,
    Input,
    OnChanges,
    OnInit,
    AfterViewInit,
    Output,
    SimpleChanges,
    ViewChild
} from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Participation } from '../../entities/participation';
import { RepositoryFileService } from '../../entities/repository/repository.service';
import { WindowRef } from '../../shared/websocket/window.service';
import { JhiAlertService } from 'ng-jhipster';
import { JhiWebsocketService } from '../../shared';
import { EditorComponent } from '../editor.component';
import 'brace/theme/dreamweaver';
declare let ace: any;

@Component({
    selector: 'jhi-editor-ace',
    templateUrl: './editor-ace.component.html',
    providers: [
        JhiAlertService,
        WindowRef,
        NgbModal,
        RepositoryFileService
    ]
})

export class EditorAceComponent implements OnInit, AfterViewInit, OnChanges {
    @ViewChild('editor') editor;

    /** Ace Editor Options **/
    editorText = '';
    editorFileSessions: object = {};
    editorMode = 'java'; // String or mode object
    editorReadOnly = false;
    editorAutoUpdate = true; // change content when editor text changes
    editorDurationBeforeCallback = 3000; // wait 3s before callback 'textChanged' sends new value

    /** Callback timing variables **/
    updateFilesDebounceTime = 3000;
    saveFileDelayTime = 2500;

    @Input() participation: Participation;
    @Input() fileName: string;
    @Output() saveStatusChange = new EventEmitter<object>();

    constructor(private parent: EditorComponent,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryFileService: RepositoryFileService,
                public modalService: NgbModal) {}

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
        console.log('EMITTING STATUS CHANGE');
        this.saveStatusChange.emit(statusChange);
    }

    /**
     * @function updateSaveStatusLabel
     * @desc Sets the labels under the ngx-treeview (files) according to the status of the files
     */
    updateSaveStatusLabel() {
        const sessionKeys = Object.keys(this.editorFileSessions);
        const unsavedFiles = sessionKeys.filter(session =>
            this.editorFileSessions[session].unsavedChanges === true).length;

        if (unsavedFiles > 0) {
            if (this.onSaveStatusChange) {
                this.onSaveStatusChange({
                    isSaved: false,
                    saveStatusLabel: '<i class="fa fa-circle-o-notch fa-spin text-info"></i> <span class="text-info">Unsaved changes in ' + unsavedFiles + ' files.</span>'
                });
            }
        } else {
            if (this.onSaveStatusChange) {
                this.onSaveStatusChange({
                    isSaved: true,
                    saveStatusLabel: '<i class="fa fa-check-circle text-success"></i> <span class="text-success"> All changes saved.</span>'
                });
            }
        }
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFile(fileName: string) {
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.get(this.participation.id, fileName).subscribe(fileObj => {

            if (!this.editorFileSessions[fileName]) {
                this.editorFileSessions[fileName] = {};
                this.editorFileSessions[fileName].code = fileObj.fileContent;
            }
            /**
             * Assign the obtained file content to the editor and set the ace mode
             * Additionally, we resize the editor window and set focus to it
             */
            this.editorText = fileObj.fileContent;
            // This fetches a list of all supported editor modes and matches it afterwards against the file extension
            const aceModeList = ace.require('ace/ext/modelist');
            const aceMode = aceModeList.getModeForPath(fileName);
            this.editor.setMode(aceMode);
            this.editor.getEditor().resize();
            this.editor._editor.focus();
        }, err => {
            console.log('There was an error while getting file', this.fileName, err);
        });
    }

    /**
     * @function saveFile
     * @desc Saves the currently selected file; is being called when the file is changed (onFileChanged)
     * @param fileName: name of currently selected file
     */
    saveFile(fileName: string) {
        if (this.onSaveStatusChange) {
            this.onSaveStatusChange({
                isSaved: false,
                saveStatusLabel: ' <i class="fa fa-circle-o-notch fa-spin text-info"></i><span class="text-info"> Saving file.</span>'
            });
        }

        this.repositoryFileService.update(this.participation.id,
            fileName,
            this.editorFileSessions[fileName].code)
            .debounceTime(this.updateFilesDebounceTime)
            .distinctUntilChanged()
            .subscribe(() => {
                this.editorFileSessions[fileName].unsavedChanges = false;
                this.updateSaveStatusLabel();
        }, err => {
            if (this.onSaveStatusChange) {
                this.onSaveStatusChange( {
                    isSaved: false,
                    saveStatusLabel: '<i class="fa fa-times-circle text-danger"></i> <span class="text-danger"> Failed to save file.</span>'
                });
            }
            console.log('There was an error while saving file', this.fileName, err);
        });
    }

    /**
     * @function onFileTextChanged
     * @desc Callback function for text changes in the Ace Editor
     * @param code {string} Current editor code
     */
    onFileTextChanged(code) {
        if (this.editorFileSessions[this.fileName] !== code && this.editorText !== '') {
            this.editorFileSessions[this.fileName].code = code;
            this.editorFileSessions[this.fileName].unsavedChanges = true;

            // Delay file save
            setTimeout(() => {
                this.saveFile(this.fileName);
            }, this.saveFileDelayTime);
            this.updateSaveStatusLabel();
        }
    }
}
