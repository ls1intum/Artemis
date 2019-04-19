import * as $ from 'jquery';
import { ActivatedRoute } from '@angular/router';
import { Component, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService } from 'ngx-webstorage';
import { Subscription } from 'rxjs/Subscription';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { tap } from 'rxjs/operators';

import { BuildLogEntryArray } from 'app/entities/build-log';

import { CourseService } from '../entities/course';
import { Participation, ParticipationService } from '../entities/participation';
import { ParticipationDataProvider } from '../course-list/exercise-list/participation-data-provider';
import { RepositoryFileService, RepositoryService } from '../entities/repository/repository.service';
import { AnnotationArray, Session } from '../entities/ace-editor';
import { WindowRef } from '../core/websocket/window.service';

import { textFileExtensions } from './text-files.json';
import { Interactable } from 'interactjs';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { ComponentCanDeactivate } from 'app/shared';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';
import { CommitState } from 'app/entities/ace-editor/commit-state.model';

@Component({
    selector: 'jhi-editor',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService, RepositoryFileService],
})
export class CodeEditorComponent implements OnInit, OnChanges, OnDestroy, ComponentCanDeactivate {
    @ViewChild(CodeEditorAceComponent) editor: CodeEditorAceComponent;

    /** Dependencies as defined by the Editor component */
    participation: Participation;
    selectedFile: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    unsavedFiles: string[] = [];
    session: Session;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

    /** Code Editor State Booleans **/
    editorState = EditorState.CLEAN;
    commitState: CommitState;
    isBuilding = false;

    /**
     * @constructor CodeEditorComponent
     * @param {ActivatedRoute} route
     * @param {ParticipationService} participationService
     * @param {ParticipationDataProvider} participationDataProvider
     * @param {RepositoryService} repositoryService
     * @param {RepositoryFileService} repositoryFileService
     * @param {LocalStorageService} localStorageService
     */
    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private participationDataProvider: ParticipationDataProvider,
        private repositoryService: RepositoryService,
        private repositoryFileService: RepositoryFileService,
        private localStorageService: LocalStorageService,
    ) {}

    /**
     * @function ngOnInit
     * @desc Fetches the participation and the repository files for the provided participationId in params
     * If we are able to find the participation with the id specified in the route params in our data storage,
     * we use it in order to spare any additional REST calls
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe(params => {
            const participationId = Number(params['participationId']);
            this.loadParticipation(participationId).then(() => this.loadFiles());
        });
    }

    private loadFiles() {
        /** Query the repositoryFileService for files in the repository */
        this.repositoryFileService
            .query(this.participation.id)
            .pipe(
                tap((files: string[]) => {
                    // do not display the README.md, because students should not edit it
                    this.repositoryFiles = files
                        // Filter Readme file that was historically in the student's assignment repo
                        .filter(value => value !== 'README.md')
                        // Remove binary files as they can't be displayed in an editor
                        .filter(filename => textFileExtensions.includes(filename.split('.').pop()));
                }),
                tap(() => this.checkIfRepositoryIsClean()),
                tap(() => this.loadSession()),
            )
            .subscribe(
                () => {},
                (error: HttpErrorResponse) => {
                    console.log('There was an error while getting files: ' + error.message + ': ' + error.error);
                },
            );
    }

    private loadParticipation(participationId: number) {
        return new Promise(resolve => {
            // Cast params id to Number or strict comparison will lead to result false (due to differing types)
            if (this.participationDataProvider.participationStorage && this.participationDataProvider.participationStorage.id === participationId) {
                // We found a matching participation in the data provider, so we can avoid doing a REST call
                this.participation = this.participationDataProvider.participationStorage;
                resolve();
            } else {
                /** Query the participationService for the participationId given by the params */
                this.participationService.findWithLatestResult(participationId).subscribe((response: HttpResponse<Participation>) => {
                    this.participation = response.body;
                    resolve();
                });
            }
        });
    }

    /**
     * @function ngOnChanges
     * @desc Checks if the repository has uncommitted changes
     */
    ngOnChanges() {
        this.checkIfRepositoryIsClean();
    }

    /**
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    canDeactivate() {
        return !this.unsavedFiles || !this.unsavedFiles.length;
    }

    /**
     * @function checkIfRepositoryIsClean
     * @desc Calls the repository service to see if the repository has uncommitted changes
     */
    checkIfRepositoryIsClean(): void {
        this.repositoryService.isClean(this.participation.id).subscribe(res => {
            this.commitState = res.isClean ? CommitState.CLEAN : CommitState.UNCOMMITTED_CHANGES;
        });
    }

    /**
     * Set the editor state.
     * @param editorState
     */
    setEditorState(editorState: EditorState) {
        this.editorState = editorState;
    }

    /**
     * Set unsaved files and check if this changes the commit state.
     * @param fileNames
     */
    setUnsavedFiles(fileNames: string[]) {
        this.unsavedFiles = fileNames;

        if (!this.unsavedFiles.length && this.editorState === EditorState.SAVING && this.commitState !== CommitState.WANTS_TO_COMMIT) {
            this.editorState = EditorState.CLEAN;
            this.commitState = CommitState.UNCOMMITTED_CHANGES;
        } else if (!this.unsavedFiles.length && this.editorState === EditorState.SAVING && this.commitState === CommitState.WANTS_TO_COMMIT) {
            this.editorState = EditorState.CLEAN;
            this.commit();
        } else if (!this.unsavedFiles.length) {
            this.editorState = EditorState.CLEAN;
        } else {
            this.editorState = EditorState.UNSAVED_CHANGES;
        }
    }

    /**
     * @function updateLatestResult
     * @desc Callback function for when a new result is received from the result component
     * @param $event Event object which contains the newly received result
     */
    updateLatestResult() {
        this.isBuilding = false;
    }

    /**
     * Check if the received build logs are recent and format them for use in the ace-editor
     * @param buildLogs
     */
    updateLatestBuildLogs(buildLogs: BuildLogEntryArray) {
        const timestamp = buildLogs.length ? Date.parse(buildLogs[0].time) : 0;
        if (!this.buildLogErrors || timestamp > this.buildLogErrors.timestamp) {
            this.buildLogErrors = { errors: buildLogs.extractErrors(), timestamp };
        }
    }

    /**
     * @function updateSelectedFile
     * @desc Callback function for when a new file is selected within the file-browser component
     * @param $event Event object which contains the new file name
     */
    updateSelectedFile($event: any) {
        this.selectedFile = $event.fileName;
    }

    /**
     * @function updateRepositoryCommitStatus
     * @desc Callback function for when a file was created or deleted; updates the current repository files
     */
    updateRepositoryCommitStatus($event: any) {
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        /** Query the repositoryFileService for updated files in the repository */
        this.repositoryFileService.query(this.participation.id).subscribe(
            files => {
                this.repositoryFiles = files
                    // Filter Readme file that was historically in the student's assignment repo
                    .filter(value => value !== 'README.md')
                    // Remove binary files as they can't be displayed in an editor
                    .filter(filename => textFileExtensions.includes(filename.split('.').pop()));
                // Select newly created file
                if ($event.mode === 'create' && this.repositoryFiles.includes($event.file)) {
                    this.selectedFile = $event.file;
                } else if ($event.file === this.selectedFile && $event.mode === 'delete' && !this.repositoryFiles.includes($event.file)) {
                    this.selectedFile = undefined;
                }
            },
            (error: HttpErrorResponse) => {
                console.log('There was an error while getting files: ' + error.message + ': ' + error.error);
            },
        );
    }

    /**
     * @function loadSession
     * @desc Gets the user's session data from localStorage to load editor settings
     */
    loadSession() {
        // Only do this if we already received a participation object from parent
        if (this.participation) {
            const sessions = JSON.parse(this.localStorageService.retrieve('sessions') || '{}');
            this.session = sessions[this.participation.id];
            if (this.session && (!this.buildLogErrors || this.session.timestamp > this.buildLogErrors.timestamp)) {
                this.buildLogErrors = {
                    errors: compose(
                        fromPairs,
                        map(([fileName, errors]) => [fileName, new AnnotationArray(...errors)]),
                        filter(([, errors]) => errors.length),
                        toPairs,
                    )(this.session.errors),
                    timestamp: this.session.timestamp,
                };
            }
        }
    }

    /**
     * @function toggleCollapse
     * @desc Collapse parts of the editor (file browser, build output...)
     * @param $event {object} Click event object; contains target information
     * @param horizontal {boolean} Used to decide which height to use for the collapsed element
     * @param interactResizable {Interactable} The interactjs element, used to en-/disable resizing
     * @param minWidth {number} Width to set the element to after toggling the collapse
     * @param minHeight {number} Height to set the element to after toggling the collapse
     */
    toggleCollapse($event: any, horizontal: boolean, interactResizable: Interactable, minWidth?: number, minHeight?: number) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        const $card = $(target).closest('.card');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            interactResizable.resizable({ enabled: true });

            // Reset min width if argument was provided
            if (minWidth) {
                $card.width(minWidth + 'px');
            }
            // Reset min height if argument was provided
            if (minHeight) {
                $card.height(minHeight + 'px');
            }
        } else {
            $card.addClass('collapsed');
            horizontal ? $card.height('35px') : $card.width('55px');
            interactResizable.resizable({ enabled: false });
        }
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     * @param $event
     */
    commit() {
        // Avoid multiple commits at the same time.
        if (this.commitState === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        if (!this.unsavedFiles.length) {
            this.commitState = CommitState.COMMITTING;
            this.repositoryService.commit(this.participation.id).subscribe(
                () => {
                    this.commitState = CommitState.CLEAN;
                    this.isBuilding = true;
                },
                err => {
                    console.log('Error during commit ocurred!', err);
                },
            );
        } else {
            this.commitState = CommitState.WANTS_TO_COMMIT;
            this.editor.saveChangedFiles();
        }
    }

    /**
     * @function ngOnDestroy
     * @desc Framework function which is executed when the component is destroyed.
     * Used for component cleanup, close open sockets, connections, subscriptions...
     */
    ngOnDestroy() {
        /** Unsubscribe onDestroy to avoid performance issues due to a high number of open subscriptions */
        this.paramSub.unsubscribe();
    }
}
