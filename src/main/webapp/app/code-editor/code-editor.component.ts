import * as $ from 'jquery';
import { ActivatedRoute } from '@angular/router';
import { Component, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService } from 'ngx-webstorage';
import { Subscription } from 'rxjs/Subscription';
import { difference as _difference } from 'lodash';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { catchError, map as rxMap, switchMap, tap, flatMap } from 'rxjs/operators';

import { BuildLogEntryArray } from 'app/entities/build-log';

import { CourseService } from '../entities/course';
import { Participation, ParticipationService } from '../entities/participation';
import { ParticipationDataProvider } from '../course-list/exercise-list/participation-data-provider';
import { RepositoryFileService, RepositoryService } from '../entities/repository/repository.service';
import { AnnotationArray, Session } from '../entities/ace-editor';
import { WindowRef } from '../core/websocket/window.service';

import Interactable from '@interactjs/core/Interactable';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { ComponentCanDeactivate } from 'app/shared';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';
import { CommitState } from 'app/entities/ace-editor/commit-state.model';
import { Observable, throwError } from 'rxjs';
import { ResultService, Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { TranslateService } from '@ngx-translate/core';
import { FileChange, RenameFileChange, CreateFileChange, DeleteFileChange, FileType } from 'app/entities/ace-editor/file-change.model';

@Component({
    selector: 'jhi-editor',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService, RepositoryFileService],
})
export class CodeEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild(CodeEditorAceComponent, { static: true }) editor: CodeEditorAceComponent;

    /** Dependencies as defined by the Editor component */
    participation: Participation;
    selectedFile: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    unsavedFiles: string[] = [];
    errorFiles: string[] = [];
    session: Session;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };
    fileChange: FileChange;

    /** Code Editor State Booleans **/
    editorState = EditorState.CLEAN;
    commitState = CommitState.UNDEFINED;
    isBuilding = false;
    isLoadingFiles = true;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private participationDataProvider: ParticipationDataProvider,
        private repositoryService: RepositoryService,
        private resultService: ResultService,
        private localStorageService: LocalStorageService,
        private jhiAlertService: JhiAlertService,
        private translateService: TranslateService,
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
            // First: Load participation, which is needed for all successive calls
            this.loadParticipation(participationId)
                .pipe(
                    // If the participation can't be found, throw a fatal error - the exercise can't be conducted without a participation
                    switchMap(participation => (participation ? Observable.of(participation) : throwError('participationNotFound'))),
                    // Load the participation with its result and result details, so that sub components don't try to also load the details
                    flatMap(participation => {
                        const latestResult = participation.results && participation.results.length ? participation.results[0] : null;
                        return latestResult
                            ? this.loadResultDetails(latestResult).pipe(
                                  rxMap(feedback => {
                                      if (feedback) {
                                          participation.results[0].feedbacks = feedback;
                                      }
                                      return participation;
                                  }),
                              )
                            : Observable.of(participation);
                    }),
                    tap(participation => (this.participation = participation)),
                    switchMap(() => this.checkIfRepositoryIsClean()),
                    tap(commitState => (this.commitState = commitState)),
                    tap(() => this.loadSession()),
                )
                .subscribe(
                    () => {},
                    err => {
                        this.commitState = CommitState.COULD_NOT_BE_RETRIEVED;
                        this.onError(err);
                    },
                );
        });
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * Try to retrieve the participation from cache, otherwise do a REST call to fetch it with the latest result.
     * @param participationId
     */
    private loadParticipation(participationId: number): Observable<Participation | null> {
        if (this.participationDataProvider.participationStorage && this.participationDataProvider.participationStorage.id === participationId) {
            // We found a matching participation in the data provider, so we can avoid doing a REST call
            return Observable.of(this.participationDataProvider.participationStorage);
        } else {
            return this.participationService.findWithLatestResult(participationId).pipe(
                catchError(() => Observable.of(null)),
                rxMap(res => res && res.body),
            );
        }
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadResultDetails(result: Result): Observable<Feedback[] | null> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            catchError(() => Observable.of(null)),
            rxMap(res => res && res.body),
        );
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
    checkIfRepositoryIsClean(): Observable<CommitState> {
        return this.repositoryService.isClean(this.participation.id).pipe(
            catchError(() => Observable.of(null)),
            rxMap(res => (res ? (res.isClean ? CommitState.CLEAN : CommitState.UNCOMMITTED_CHANGES) : CommitState.COULD_NOT_BE_RETRIEVED)),
        );
    }

    /**
     * Store the build log error data in the localStorage of the browser (synchronous action).
     */
    storeSession() {
        this.localStorageService.store('sessions', JSON.stringify({ [this.participation.id]: this.buildLogErrors }));
    }

    /**
     * Set the editor state.
     * @param editorState
     */
    setEditorState(editorState: EditorState) {
        this.editorState = editorState;
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.jhiAlertService.error(`arTeMiSApp.editor.errors.${error}`);
    }

    /**
     * Set unsaved files and check if this changes the commit state.
     * @param unsavedFiles
     */
    setUnsavedFiles(unsavedFiles: string[]) {
        this.unsavedFiles = unsavedFiles;
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
     */
    updateLatestResult() {
        this.isBuilding = false;
    }

    /**
     * Check if the received build logs are recent and format them for use in the ace-editor
     * @param buildLogs
     */
    updateLatestBuildLogs(buildLogs: BuildLogEntryArray) {
        // The build logs come asynchronously while the view of other components are rendered.
        // To avoid ExpressionChangedAfterItHasBeenCheckedError, we wait a tick so the view can update.
        setTimeout(() => {
            const timestamp = buildLogs.length ? Date.parse(buildLogs[0].time) : 0;
            if (!this.buildLogErrors || timestamp > this.buildLogErrors.timestamp) {
                this.buildLogErrors = { errors: buildLogs.extractErrors(), timestamp };
                this.errorFiles = Object.keys(this.buildLogErrors.errors);
                // Only store the buildLogErrors if the session was already loaded - might be that they are outdated
                if (this.session) {
                    this.storeSession();
                }
            }
        }, 0);
    }

    /**
     * @function onFileChange
     * @desc A file has changed (create, rename, delete), so we have uncommitted changes.
     * Also all references to a file need to be updated in case of rename,
     * in case of delete make sure to also remove all sub entities (files in folder).
     */
    onFileChange<T extends FileChange>([files, fileChange]: [string[], T]) {
        this.commitState = CommitState.UNCOMMITTED_CHANGES;
        this.repositoryFiles = files;
        if (fileChange instanceof CreateFileChange) {
            // Select newly created file
            if (fileChange.fileType === FileType.FILE) {
                this.selectedFile = fileChange.fileName;
            }
        } else if (fileChange instanceof RenameFileChange) {
            const oldFileNameRegex = new RegExp(`^${fileChange.oldFileName}`);
            const renamedUnsavedFiles = this.unsavedFiles
                .filter(file => file.startsWith(fileChange.oldFileName))
                .map(file => file.replace(oldFileNameRegex, fileChange.newFileName));
            this.unsavedFiles = [...this.unsavedFiles.filter(file => !file.startsWith(fileChange.oldFileName)), ...renamedUnsavedFiles];
            const renamedErrorFiles = this.errorFiles.filter(file => file.startsWith(fileChange.oldFileName)).map(file => file.replace(oldFileNameRegex, fileChange.newFileName));
            this.errorFiles = [...this.errorFiles.filter(file => !file.startsWith(fileChange.oldFileName)), ...renamedErrorFiles];
            const renamedErrors = compose(
                fromPairs,
                map(([fileName, session]) => [fileName.replace(oldFileNameRegex, fileChange.newFileName), session]),
                toPairs,
            )(this.buildLogErrors.errors);
            const filteredErrors = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWith(fileChange.oldFileName)),
                toPairs,
            )(this.buildLogErrors.errors);
            this.buildLogErrors = { errors: { ...filteredErrors, ...renamedErrors }, timestamp: this.buildLogErrors.timestamp };
            this.fileChange = fileChange;
            // If the renamed file has errors, we also need to update the session in localStorage
            if (this.errorFiles.includes(fileChange.newFileName)) {
                this.storeSession();
            }
            // Also updated the name of the selectedFile
            if (this.selectedFile && fileChange.oldFileName === this.selectedFile) {
                this.selectedFile = fileChange.newFileName;
            } else if (this.selectedFile && this.selectedFile.startsWith(fileChange.oldFileName)) {
                this.selectedFile = this.selectedFile.replace(oldFileNameRegex, fileChange.newFileName);
            }
        } else if (fileChange instanceof DeleteFileChange) {
            this.fileChange = fileChange;
            this.unsavedFiles = this.unsavedFiles.filter(fileName => !fileName.startsWith(fileChange.fileName));
            this.errorFiles = this.errorFiles.filter(fileName => !fileName.startsWith(fileChange.fileName));
            const errors = compose(
                fromPairs,
                filter(([fileName]) => !fileName.startsWith(fileChange.fileName)),
                toPairs,
            )(this.buildLogErrors.errors);
            this.buildLogErrors = { errors, timestamp: this.buildLogErrors.timestamp };
            // If the selected file or its containing folder was deleted, unselect it
            if (this.selectedFile && (this.selectedFile === fileChange.fileName || this.selectedFile.startsWith(fileChange.fileName))) {
                this.selectedFile = undefined;
            }
        }
        if (this.unsavedFiles.length && this.editorState === EditorState.CLEAN) {
            this.editorState = EditorState.UNSAVED_CHANGES;
        } else if (!this.unsavedFiles.length && this.editorState === EditorState.UNSAVED_CHANGES) {
            this.editorState = EditorState.CLEAN;
        }
    }

    /**
     * @function loadSession
     * @desc Gets the user's session data from localStorage to load editor settings
     */
    loadSession() {
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

            this.errorFiles = Object.keys(this.buildLogErrors.errors);
        } else if (this.buildLogErrors) {
            this.storeSession();
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
     * When files were saved, check which could be saved and set unsavedFiles to update the ui.
     * Files that could not be saved will show an error in the header.
     * @param files
     */
    onSavedFiles(files: any) {
        const { errorFiles, savedFiles } = Object.entries(files).reduce(
            (acc, [fileName, error]: [string, string | null]) =>
                error ? { ...acc, errorFiles: [fileName, ...acc.errorFiles] } : { ...acc, savedFiles: [fileName, ...acc.savedFiles] },
            { errorFiles: [], savedFiles: [] },
        );

        const unsavedFiles = _difference(this.unsavedFiles, savedFiles);
        this.setUnsavedFiles(unsavedFiles);

        if (errorFiles.length) {
            this.onError('saveFailed');
        }
        this.storeSession();
    }

    /**
     * When the files are loaded set the repositoryFiles.
     * @param files
     */
    onFilesLoaded(files: string[]) {
        this.repositoryFiles = files;
    }

    /**
     * When the content of a file changes, set it as unsaved.
     * @param file
     */
    onFileContentChange({ file }: { file: string; unsavedChanges: boolean }) {
        const unsavedFiles = this.unsavedFiles.includes(file) ? this.unsavedFiles : [file, ...this.unsavedFiles];
        this.setUnsavedFiles(unsavedFiles);
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
