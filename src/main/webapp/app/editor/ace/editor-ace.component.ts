import {ResultService} from '../../entities/result';
import {RepositoryFileService, RepositoryService} from '../../entities/repository/repository.service';
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
import {ExerciseParticipationService, Participation, ParticipationService} from '../../entities/participation';
import {WindowRef} from '../../shared/websocket/window.service';
import {JhiAlertService} from 'ng-jhipster';
import {CourseExerciseService} from '../../entities/course';
import {JhiWebsocketService} from '../../shared';
import {EditorComponent} from '../editor.component';

@Component({
    selector: 'jhi-editor-ace',
    templateUrl: './editor-ace.component.html',
    providers: [
        JhiAlertService,
        WindowRef,
        ResultService,
        RepositoryService,
        CourseExerciseService,
        ParticipationService,
        ExerciseParticipationService,
        NgbModal,
        RepositoryFileService
    ]
})

export class EditorAceComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
    @ViewChild('editor') editor;
    commonFilePathPrefix: string;

    /**
     * Ace Editor Options
     */

    editorText: string = ''; // possible two way binding
    editorFileSessions : object = {};
    editorMode; //string or object
    editorOptions;
    editorReadOnly: boolean = false;
    editorAutoUpdate: boolean = true; //change content when [text] change
    editorDurationBeforeCallback = 1000; //wait 1s before callback 'textChanged' sends new value

    @Input() participation: Participation;
    @Input() fileName: string;
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
        this.editor.setTheme('chrome');
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.participation && this.participation) {
            this.updateSaveStatusLabel();
            console.log(this.participation.student.login);
        }
        if (changes.fileName && this.fileName) {
            console.log('FILE CHANGED, loading file: ' + this.fileName);
            if (!this.commonFilePathPrefix) {
                this.commonFilePathPrefix = this.identifyCommonFilePathPrefix();
            }
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

        if(unsavedFiles > 0) {
            if(this.onSaveStatusChange) {
                this.onSaveStatusChange({
                    bIsSaved: false,
                    saveStatusLabel: '<i class="fa fa-circle-o-notch fa-spin text-info"></i> <span class="text-info">Unsaved changes in ' + unsavedFiles + ' files.</span>'
                });
            }
        } else {
            if(this.onSaveStatusChange) {
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
     * Looks for the users identifier within the repository path to identify the common file path
     * which we need to append to the filename when doing a GET-request to the server
     * @returns {string}: the prefix path until the actual repository appended by a backslash
     */
    identifyCommonFilePathPrefix(): string {
        const studentLoginIdx = this.parent.repositoryFiles[0]
            .split('\\')
            .indexOf(this.participation.student.login);
        return this.parent.repositoryFiles[0].split('\\').slice(0, studentLoginIdx).join('\\') + '\\';
    }

    /**
     * Callback function for text changes in the Ace Editor
     * @param code
     */
    onFileChanged(code) {
        console.log('new code', code);
        const currentFileExtendedName = this.commonFilePathPrefix +
            this.fileName.replace(new RegExp('/', 'g'), '\\');
        console.log('onFileChanged with ext file name: ' + currentFileExtendedName);
        this.editorFileSessions[currentFileExtendedName].code = code;
        this.editorFileSessions[currentFileExtendedName].unsavedChanges = true;

        /**
         * Need to pass the exact and fully extended fileName into the HTTP Put
         */
        this.saveFile(currentFileExtendedName);
        this.updateSaveStatusLabel();
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy(): void {}

}
