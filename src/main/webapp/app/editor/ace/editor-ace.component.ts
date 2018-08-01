import {RepositoryFileService} from '../../entities/repository/repository.service';
import {
    Component,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    AfterViewInit,
    Output,
    SimpleChanges,
    ViewChild
} from '@angular/core';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {Participation} from '../../entities/participation';
import {WindowRef} from '../../shared/websocket/window.service';
import {JhiAlertService} from 'ng-jhipster';
import {JhiWebsocketService} from '../../shared';
import {EditorComponent} from '../editor.component';
import 'brace/theme/clouds';

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

export class EditorAceComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
    @ViewChild('editor') editor;

    /**
     * Ace Editor Options
     */
    editorText = ''; // possible two way binding
    editorFileSessions: object = {};
    editorMode = 'java'; // string or object
    editorOptions;
    editorReadOnly = false;
    editorAutoUpdate = true; // change content when [text] change
    editorDurationBeforeCallback = 3000; // wait 3s before callback 'textChanged' sends new value

    @Input() participation: Participation;
    @Input() fileName: string;
    @Input() commonFilePathPrefix: string;
    @Output() saveStatusChange = new EventEmitter<object>();

    constructor(private parent: EditorComponent,
                private jhiWebsocketService: JhiWebsocketService,
                private repositoryFileService: RepositoryFileService,
                public modalService: NgbModal) {
    }

    /**
     * @function ngOnInit
     * @desc Framework function which is executed when the component is instantiated.
     * Used to assign parameters which are used by the component
     */
    ngOnInit(): void {
        this.updateSaveStatusLabel();
    }

    /**
     * @function ngAfterViewInit
     * @desc Framework lifecycle hook that is called after Angular has fully initialized a component's view;
     * used to handle any additional initialization tasks
     */
    ngAfterViewInit(): void {
        this.editor.setTheme('clouds');
    }

    /**
     * @function ngOnChanges
     * @desc Framework lifecycle hook that is called when any data-bound property of a directive changes
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.participation && this.participation) {
            this.updateSaveStatusLabel();
        }
        if (changes.fileName && this.fileName) {
            console.log('FILE CHANGED, loading file: ' + this.fileName);
            // current file has changed
            this.loadFile(
                this.commonFilePathPrefix +
                this.fileName.replace(new RegExp('/', 'g'), '\\')
            );
        }
    }

    onSaveStatusChange(statusChange: object) {
        console.log('EMITTING STATUS CHANGE');
        this.saveStatusChange.emit(statusChange);
    }

    updateSaveStatusLabel() {

        // TODO: check filter function
        const sessionKeys = Object.keys(this.editorFileSessions);
        const unsavedFiles = sessionKeys.filter(session =>
            this.editorFileSessions[session].unsavedChanges === true).length;

        if (unsavedFiles > 0) {
            if (this.onSaveStatusChange) {
                this.onSaveStatusChange({
                    bIsSaved: false,
                    saveStatusLabel: '<i class="fa fa-circle-o-notch fa-spin text-info"></i> <span class="text-info">Unsaved changes in ' + unsavedFiles + ' files.</span>'
                });
            }
        } else {
            if (this.onSaveStatusChange) {
                this.onSaveStatusChange({
                    bIsSaved: true,
                    saveStatusLabel: '<i class="fa fa-check-circle text-success"></i> <span class="text-success"> All changes saved.</span>'
                });
            }
        }
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param extendedFileName: File of the name to be opened in the editor
     */
    loadFile(extendedFileName: string) {
        /** Query the repositoryFileService for the specified file in the repository */
        this.repositoryFileService.get(this.participation.id, extendedFileName).subscribe(fileObj => {

            if (!this.editorFileSessions[extendedFileName]) {
                // TODO: check how to automatically set editor (brace) mode
                // var ModeList = ace.require("ace/ext/modelist");
                // var mode = ModeList.getModeForPath(file).mode;
                console.log('Loaded file with ext file name' + extendedFileName);
                this.editorFileSessions[extendedFileName] = {};
                this.editorFileSessions[extendedFileName].code = fileObj.fileContent;

                console.log(this.editorFileSessions);
            }

            this.editorText = fileObj.fileContent;
            this.editor.nativeElement.focus();

        }, err => {
            console.log('There was an error while getting file: ' + this.fileName);
            console.log(err);
        });
    }

    /**
     * @function saveFile
     * @desc Saved the currently selected file; is being called when the file is changed (onFileChanged)
     * @param extendedFileName: name of currently selected file
     */
    saveFile(extendedFileName: string) {
        console.log('Saving ' + this.fileName);

        if (this.onSaveStatusChange) {
            this.onSaveStatusChange({
                bIsSaved: false,
                saveStatusLabel: ' <i class="fa fa-circle-o-notch fa-spin text-info"></i><span class="text-info"> Saving file.</span>'
            });
        }

        this.repositoryFileService.update(this.participation.id,
            extendedFileName,
            this.editorFileSessions[extendedFileName].code)
            .debounceTime(3000)
            .distinctUntilChanged()
            .subscribe(fileObj => {
                console.log('saved file: ' + this.fileName);
                this.editorFileSessions[extendedFileName].unsavedChanges = false;
                this.updateSaveStatusLabel();
        }, err => {
            if (this.onSaveStatusChange) {
                this.onSaveStatusChange( {
                    bIsSaved: false,
                    saveStatusLabel: '<i class="fa fa-times-circle text-danger"></i> <span class="text-danger"> Failed to save file.</span>'
                });
            }
            console.log('There was an error while saving file: ' + this.fileName);
            console.log(err);
        });
    }

    /**
     * Callback function for text changes in the Ace Editor
     * @param code
     */
    onFileTextChanged(code) {
        console.log('new code', code);
        const currentFileExtendedName = this.commonFilePathPrefix +
            this.fileName.replace(new RegExp('/', 'g'), '\\');
        if (this.editorFileSessions[currentFileExtendedName] !== code && this.editorText !== '') {
            console.log('onFileChanged with ext file name: ' + currentFileExtendedName);
            this.editorFileSessions[currentFileExtendedName].code = code;
            this.editorFileSessions[currentFileExtendedName].unsavedChanges = true;

            /**
             * Need to pass the exact and fully extended fileName into the HTTP Put
             */
            this.saveFile(currentFileExtendedName);
            this.updateSaveStatusLabel();
        }
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy(): void {}

}
