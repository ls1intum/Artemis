import * as $ from 'jquery';
import { ActivatedRoute } from '@angular/router';
import { Component, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
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
import { AnnotationArray, Session, EditorFileSession as EFS, FileSessions, TextChange } from '../entities/ace-editor';
import { WindowRef } from '../core/websocket/window.service';

import { textFileExtensions } from './text-files.json';
import { Interactable } from 'interactjs';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { ComponentCanDeactivate } from 'app/shared';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';
import { CommitState } from 'app/entities/ace-editor/commit-state.model';
import { Observable, throwError } from 'rxjs';
import { ResultService, Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-editor',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService, RepositoryFileService],
})
export class CodeEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild(CodeEditorAceComponent) editor: CodeEditorAceComponent;

    /** Dependencies as defined by the Editor component */
    participation: Participation;
    selectedFile: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    unsavedFiles: string[] = [];
    errorFiles: string[] = [];
    session: Session;
    editorFileSession = EFS.create();
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

    /** Code Editor State Booleans **/
    editorState = EditorState.CLEAN;
    commitState = CommitState.UNDEFINED;
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
                    switchMap(() => this.loadFiles()),
                    tap(files => {
                        this.editorFileSession = EFS.create();
                        this.editorFileSession = EFS.addNewFiles(this.editorFileSession, ...files);
                    }),
                    tap(files => (this.repositoryFiles = files)),
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
     * Load files from the participants repository.
     * Files that are not relevant for the conduction of the exercise are removed from result.
     */
    private loadFiles(): Observable<string[]> {
        return this.repositoryFileService.query(this.participation.id).pipe(
            rxMap((files: string[]) =>
                files
                    // Filter Readme file that was historically in the student's assignment repo
                    .filter(value => !value.includes('README.md'))
                    // Remove binary files as they can't be displayed in an editor
                    .filter(filename => textFileExtensions.includes(filename.split('.').pop())),
            ),
            catchError((error: HttpErrorResponse) => {
                console.log('There was an error while getting files: ' + error.message + ': ' + error.error);
                return Observable.of([]);
            }),
        );
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
     * @param fileNames
     */
    setUnsavedFiles() {
        this.unsavedFiles = EFS.getUnsavedFileNames(this.editorFileSession);

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
            this.editorFileSession = EFS.setErrorsFromBuildLogs(
                this.editorFileSession,
                ...Object.entries(this.buildLogErrors.errors).map(([fileName, annotations]): [string, AnnotationArray] => [fileName, annotations as AnnotationArray]),
            );
            this.errorFiles = Object.keys(this.buildLogErrors.errors);
            // Only store the buildLogErrors if the session was already loaded - might be that they are outdated
            if (this.session) {
                this.storeSession();
            }
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
        this.loadFiles()
            .pipe(
                tap(() => {
                    if ($event.mode === 'rename') {
                        this.editorFileSession = EFS.renameFile(this.editorFileSession, $event.oldFileName, $event.newFileName);
                        this.repositoryFiles = [...this.repositoryFiles.filter(file => file !== $event.oldFileName), $event.newFilename];
                    }
                }),
                tap(files => {
                    const newFiles: string[] = _difference(files, this.repositoryFiles);
                    const removedFiles: string[] = _difference(this.repositoryFiles, files);
                    this.editorFileSession = EFS.update(this.editorFileSession, newFiles, removedFiles);
                }),
                tap(files => (this.repositoryFiles = files)),
                tap(() => {
                    if ($event.mode === 'create' && this.repositoryFiles.includes($event.file)) {
                        // Select newly created file
                        this.selectedFile = $event.file;
                    } else if ($event.mode === 'rename' && $event.oldFileName === this.selectedFile) {
                        this.selectedFile = this.repositoryFiles.includes($event.newFileName) ? $event.newFileName : null;
                    } else if ($event.file === this.selectedFile && $event.mode === 'delete' && !this.repositoryFiles.includes($event.file)) {
                        // If the selected file was deleted, unselect it
                        this.selectedFile = undefined;
                    }
                }),
            )
            .subscribe();
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

            this.editorFileSession = EFS.setErrorsFromBuildLogs(
                this.editorFileSession,
                ...Object.entries(this.buildLogErrors.errors).map(([fileName, annotations]): [string, AnnotationArray] => [fileName, annotations as AnnotationArray]),
            );
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

    onSavedFiles(files: any) {
        const { errorFiles, savedFiles } = Object.entries(files).reduce(
            (acc, [fileName, error]: [string, string | null]) =>
                error ? { ...acc, errorFiles: [fileName, ...acc.errorFiles] } : { ...acc, savedFiles: [fileName, ...acc.savedFiles] },
            { errorFiles: [], savedFiles: [] },
        );

        this.editorFileSession = EFS.setSaved(this.editorFileSession, ...savedFiles);
        this.setUnsavedFiles();

        if (errorFiles.length) {
            this.onError('saveFailed');
        }
    }

    onFileContentChange({
        file,
        code,
        unsavedChanges,
        errors,
        cursor,
    }: {
        file: string;
        code: string;
        unsavedChanges: boolean;
        errors: AnnotationArray;
        cursor: { column: number; row: number };
    }) {
        this.editorFileSession = EFS.setCode(this.editorFileSession, file, code);
        this.editorFileSession = EFS.setErrors(this.editorFileSession, file, errors);
        this.editorFileSession = EFS.setCursor(this.editorFileSession, file, cursor);
        if (unsavedChanges) {
            this.editorFileSession = EFS.setUnsaved(this.editorFileSession, file);
            this.setUnsavedFiles();
        }
    }

    onAnnotationChange({ file, change }: { file: string; change: TextChange }) {
        this.editorFileSession = EFS.updateErrorPositions(this.editorFileSession, file, change);
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
