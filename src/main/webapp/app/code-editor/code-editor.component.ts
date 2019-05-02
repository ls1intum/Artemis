import * as $ from 'jquery';
import { Component, EventEmitter, Input, Output, OnChanges, ViewChild, SimpleChanges } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService } from 'ngx-webstorage';
import { Subscription } from 'rxjs/Subscription';
import { compose, filter, fromPairs, map, toPairs } from 'lodash/fp';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';

import { BuildLogEntryArray } from 'app/entities/build-log';

import { CourseService } from '../entities/course';
import { Participation, hasParticipationChanged } from '../entities/participation';
import { RepositoryFileService, RepositoryService } from '../entities/repository/repository.service';
import { AnnotationArray, Session } from '../entities/ace-editor';
import { WindowRef } from '../core/websocket/window.service';

import { textFileExtensions } from './text-files.json';
import Interactable from '@interactjs/core/Interactable';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { EditorState } from 'app/entities/ace-editor/editor-state.model';
import { CommitState } from 'app/entities/ace-editor/commit-state.model';
import { Observable } from 'rxjs';
import { ResultService, Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-code-editor',
    templateUrl: './code-editor.component.html',
    providers: [JhiAlertService, WindowRef, CourseService, RepositoryFileService],
})
export class CodeEditorComponent implements OnChanges {
    @ViewChild(CodeEditorAceComponent) editor: CodeEditorAceComponent;

    /** Dependencies as defined by the Editor component */
    participationValue: Participation;
    @Output()
    participationChange = new EventEmitter<Participation>();
    @Input()
    readonly exercise: ProgrammingExercise;
    @Input()
    readonly editableInstructions = false;
    selectedFile: string;
    paramSub: Subscription;
    repositoryFiles: string[];
    unsavedFiles: string[] = [];
    errorFiles: string[] = [];
    session: Session;
    buildLogErrors: { errors: { [fileName: string]: AnnotationArray }; timestamp: number };

    /** Code Editor State Booleans **/
    isInitial = true;
    editorState = EditorState.CLEAN;
    commitState = CommitState.UNDEFINED;
    isBuilding = false;

    @Input()
    get participation() {
        return this.participationValue;
    }

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(participation);
    }

    constructor(
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
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            this.isInitial = true;
        }
        if (this.isInitial && this.participation && this.exercise) {
            this.isInitial = false;
            Observable.of(this.participation)
                .flatMap(participation =>
                    this.loadLatestResult(this.participation).pipe(
                        switchMap(result =>
                            result
                                ? this.loadResultDetails(result).pipe(
                                      rxMap(feedback => {
                                          if (feedback) {
                                              participation.results[0].feedbacks = feedback;
                                          }
                                          return participation;
                                      }),
                                  )
                                : Observable.of(participation),
                        ),
                        tap(participationWithResults => (this.participation = participationWithResults)),
                        switchMap(() => this.checkIfRepositoryIsClean()),
                        tap(commitState => (this.commitState = commitState)),
                        switchMap(() => this.loadFiles()),
                        tap(files => (this.repositoryFiles = files)),
                        tap(() => this.loadSession()),
                    ),
                )
                .subscribe(
                    () => {},
                    err => {
                        this.commitState = CommitState.COULD_NOT_BE_RETRIEVED;
                        this.onError(err);
                    },
                );
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

    loadLatestResult(participation: Participation): Observable<Result | null> {
        if (participation && participation.results) {
            return Observable.of(participation.results.length ? participation.results[0] : null);
        }
        return this.resultService
            .findResultsForParticipation(this.exercise.course.id, this.exercise.id, participation.id)
            .pipe(rxMap(({ body: results }) => (results.length ? results.reduce((acc, result) => (result > acc ? result : acc)) : null)));
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
                tap(
                    files => (this.repositoryFiles = files),
                    tap(() => {
                        if ($event.mode === 'create' && this.repositoryFiles.includes($event.file)) {
                            // Select newly created file
                            this.selectedFile = $event.file;
                        } else if ($event.file === this.selectedFile && $event.mode === 'delete' && !this.repositoryFiles.includes($event.file)) {
                            // If the selected file was deleted, unselect it
                            this.selectedFile = undefined;
                        }
                    }),
                ),
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
     * The user will be warned if there are unsaved changes when trying to leave the code-editor.
     */
    hasUnsavedChanges() {
        return !this.unsavedFiles || !this.unsavedFiles.length;
    }
}
