import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { Observable, of, Subscription, throwError } from 'rxjs';
import { isEmpty as _isEmpty } from 'lodash-es';
import { CodeEditorSubmissionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-submission.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { CodeEditorResolveConflictModalComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-resolve-conflict-modal.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService, ConnectionError } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CommitState, EditorState, FileSubmission, GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorConfirmRefreshModalComponent } from './code-editor-confirm-refresh-modal.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { faCircleNotch, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faPlayCircle } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
})
export class CodeEditorActionsComponent implements OnInit, OnDestroy, OnChanges {
    CommitState = CommitState;
    EditorState = EditorState;
    FeatureToggle = FeatureToggle;

    @Input()
    buildable = true;
    @Input()
    unsavedFiles: { [fileName: string]: string };
    @Input() disableActions = false;
    @Input()
    get editorState() {
        return this.editorStateValue;
    }
    @Input()
    get commitState() {
        return this.commitStateValue;
    }

    @Output()
    commitStateChange = new EventEmitter<CommitState>();
    @Output()
    editorStateChange = new EventEmitter<EditorState>();
    @Output()
    isBuildingChange = new EventEmitter<boolean>();
    @Output()
    onSavedFiles = new EventEmitter<{ [fileName: string]: string | undefined }>();
    @Output()
    onRefreshFiles = new EventEmitter();
    @Output()
    onError = new EventEmitter<string>();

    isBuilding: boolean;
    editorStateValue: EditorState;
    commitStateValue: CommitState;
    isResolvingConflict = false;

    conflictStateSubscription: Subscription;
    submissionSubscription: Subscription;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    // Icons
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faSync = faSync;
    farPlayCircle = faPlayCircle;

    set commitState(commitState: CommitState) {
        this.commitStateValue = commitState;
        this.commitStateChange.emit(commitState);
    }

    set editorState(editorState: EditorState) {
        this.editorStateValue = editorState;
        this.editorStateChange.emit(editorState);
    }

    constructor(
        private repositoryService: CodeEditorRepositoryService,
        private repositoryFileService: CodeEditorRepositoryFileService,
        private conflictService: CodeEditorConflictStateService,
        private modalService: NgbModal,
        private submissionService: CodeEditorSubmissionService,
    ) {}

    ngOnInit(): void {
        this.conflictStateSubscription = this.conflictService.subscribeConflictState().subscribe((gitConflictState: GitConflictState) => {
            // When the conflict is encountered when opening the code-editor, setting the commitState here could cause an uncheckedException.
            // This is why a timeout of 0 is set to make sure the template is rendered before setting the commitState.
            if (this.commitState === CommitState.CONFLICT && gitConflictState === GitConflictState.OK) {
                // Case a: Conflict was resolved.
                setTimeout(() => (this.commitState = CommitState.UNDEFINED), 0);
            } else if (this.commitState !== CommitState.CONFLICT && gitConflictState === GitConflictState.CHECKOUT_CONFLICT) {
                // Case b: Conflict has occurred.
                setTimeout(() => (this.commitState = CommitState.CONFLICT), 0);
            }
        });
        this.submissionSubscription = this.submissionService
            .getBuildingState()
            .pipe(tap((isBuilding: boolean) => (this.isBuilding = isBuilding)))
            .subscribe();

        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                this.autoSaveTimer = 0;
                this.onSave();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * After save and commit, we need to wait for the 'save' to settle, see the setter {@link CodeEditorContainerComponent#unsavedFilesValue}.
     * This is because the user might have changed files while the commit was executing.
     * In that case, we do not reset the commit state to CommitState.CLEAN.
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        setTimeout(() => {
            if (changes.editorState && changes.editorState.previousValue === EditorState.SAVING && this.commitState === CommitState.COMMITTING) {
                if (changes.editorState.currentValue === EditorState.CLEAN) {
                    this.commitState = CommitState.CLEAN;
                } else {
                    this.commitState = CommitState.UNCOMMITTED_CHANGES;
                }
            }
        });
    }

    ngOnDestroy(): void {
        clearInterval(this.autoSaveInterval);
        this.onSave();

        if (this.conflictStateSubscription) {
            this.conflictStateSubscription.unsubscribe();
        }
    }

    onRefresh() {
        if (this.editorState !== EditorState.CLEAN) {
            const modal = this.modalService.open(CodeEditorConfirmRefreshModalComponent, { keyboard: true, size: 'lg' });
            modal.componentInstance.shouldRefresh.subscribe(() => {
                this.executeRefresh();
            });
        } else {
            this.executeRefresh();
        }
    }

    executeRefresh() {
        this.editorState = EditorState.REFRESHING;
        this.repositoryService.pull().subscribe({
            next: () => {
                this.onRefreshFiles.emit();
                this.editorState = EditorState.CLEAN;
            },
            error: (error: Error) => {
                this.editorState = EditorState.UNSAVED_CHANGES;
                if (error.message === ConnectionError.message) {
                    this.onError.emit('refreshFailed' + error.message);
                } else {
                    this.onError.emit('refreshFailed');
                }
            },
        });
    }

    onSave() {
        this.saveChangedFiles()
            .pipe(catchError(() => of()))
            .subscribe();
    }

    /**
     * @function saveFiles
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles(andCommit = false): Observable<any> {
        if (!_isEmpty(this.unsavedFiles)) {
            this.editorState = EditorState.SAVING;
            const unsavedFiles = Object.entries(this.unsavedFiles).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            return this.repositoryFileService.updateFiles(unsavedFiles, andCommit).pipe(
                tap((fileSubmission: FileSubmission) => {
                    this.onSavedFiles.emit(fileSubmission);
                }),
                catchError((error: Error) => {
                    this.editorState = EditorState.UNSAVED_CHANGES;
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('saveFailed' + error.message);
                    } else {
                        this.onError.emit('saveFailed');
                    }
                    return throwError(() => error);
                }),
            );
        }
        return of(null);
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     */
    commit() {
        // Avoid multiple commits at the same time.
        if (this.commitState === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        of(null)
            .pipe(
                tap(() => (this.commitState = CommitState.COMMITTING)),
                switchMap(() => {
                    if (!_isEmpty(this.unsavedFiles)) {
                        return this.saveChangedFiles(true);
                    } else {
                        return this.repositoryService.commit();
                    }
                }),
                tap(() => {
                    if (this.editorState === EditorState.CLEAN) {
                        this.commitState = CommitState.CLEAN;
                    }
                    // We just assume that after the commit a build happens if the repo is buildable.
                    if (this.buildable) {
                        // Note: this is not 100% clean, but not setting it here would complicate the state model.
                        this.isBuilding = true;
                    }
                }),
            )
            .subscribe({
                error: (error: Error) => {
                    this.commitState = CommitState.UNCOMMITTED_CHANGES;
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('commitFailed' + error.message);
                    } else {
                        this.onError.emit('commitFailed');
                    }
                },
            });
    }

    resetRepository() {
        const modal = this.modalService.open(CodeEditorResolveConflictModalComponent, { keyboard: true, size: 'lg' });
        modal.componentInstance.shouldReset.subscribe(() => {
            this.repositoryService.resetRepository().subscribe({
                next: () => {
                    this.conflictService.notifyConflictState(GitConflictState.OK);
                    this.executeRefresh();
                },
                error: () => {
                    this.onError.emit('resetFailed');
                },
            });
        });
    }
}
