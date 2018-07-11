import {ResultService} from '../../entities/result';
import {RepositoryFileService, RepositoryService} from '../../entities/repository/repository.service';
import {Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges} from '@angular/core';
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

export class EditorAceComponent implements OnInit, OnDestroy, OnChanges {

    sessions : object = {};

    /**
     * Ace Editor Options
     */

    editorText: string = ''; // possible two way binding
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

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.participation && this.participation) {
            this.updateSaveStatusLabel();
        }
        if (changes.fileName && this.fileName) {
            console.log('FILE CHANGED');
            // current file has changed
            this.loadFile(this.fileName);
        }
    }

    onSaveStatusChange(statusChange: object) {
        console.log('EMITTING STATUS CHANGE');
        this.saveStatusChange.emit(statusChange);
    }

    updateSaveStatusLabel() {

        // TODO: check filter function
        const sessionKeys = Object.keys(this.sessions);
        const unsavedFiles = sessionKeys.filter(session => session['unsavedChanges'] == true).length;

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

    // Open the file, given by filename
    // If the file was not opened before, a new ACE EditSession for the file is created
    loadFile(fileName) {

        /** Query the repositoryFileService for the specified file in the repository */
        // TODO: pass filename into get
        this.repositoryFileService.get(this.participation.id, fileName).subscribe(fileObj => {

            console.log('We got a fileObj!');
            console.log(fileObj);

            // if(!this.sessions[fileName]) {
                // var ModeList = ace.require("ace/ext/modelist");
                // var mode = ModeList.getModeForPath(file).mode;

                // vm.sessions[file] = new ace.EditSession(fileObj.fileContent, mode);
                // vm.sessions[file].file = file;
                // vm.sessions[file].on("change", function (e) {
                //     $timeout(function() {
                //         onFileChanged(vm.sessions[file]);
                //     });
                // });

            // }
            // vm.editor.setSession(vm.sessions[file]);
            // vm.editor.focus();
        }, err => {
            console.log('There was an error while getting file: ' + err.body.msg);
        });
    }

    /**
     * Callback function for text changes in the Ace Editor
     * @param code
     */
    onEditorTextChanged(code) {
        console.log("new code", code);
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy(): void {}

}
