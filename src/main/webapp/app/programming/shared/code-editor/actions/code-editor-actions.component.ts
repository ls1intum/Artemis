import { ChangeDetectionStrategy, Component, NgZone, OnDestroy, OnInit, effect, inject, input, output, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { isEmpty as _isEmpty } from 'lodash-es';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { faCircleNotch, faExternalLink, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faPlayCircle } from '@fortawesome/free-regular-svg-icons';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getLocalRepositoryLink } from 'app/shared/util/navigation.utils';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService, ConnectionError } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { CommitState, EditorState, FileSubmission, GitConflictState } from '../model/code-editor.model';
import { CodeEditorConfirmRefreshModalComponent } from 'app/programming/shared/code-editor/actions/refresh-modal/code-editor-confirm-refresh-modal.component';
import { CodeEditorResolveConflictModalComponent } from 'app/programming/shared/code-editor/actions/conflict-modal/code-editor-resolve-conflict-modal.component';

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FeatureToggleDirective, NgbTooltip, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, RouterLink],
})
export class CodeEditorActionsComponent implements OnInit, OnDestroy {
    private readonly repositoryService = inject(CodeEditorRepositoryService);
    private readonly repositoryFileService = inject(CodeEditorRepositoryFileService);
    private readonly conflictService = inject(CodeEditorConflictStateService);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);
    private readonly submissionService = inject(CodeEditorSubmissionService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly ngZone = inject(NgZone);

    CommitState = CommitState;
    EditorState = EditorState;
    FeatureToggle = FeatureToggle;

    readonly buildable = input(true);
    readonly unsavedFiles = input<{
        [fileName: string]: string;
    }>(undefined!);
    readonly disableActions = input(false);
    readonly disableAutoSave = input(false);
    readonly participation = input<Participation>();

    // editorState / commitState use input + writable signal + manual *Change output instead of model<>().
    // Rationale (see PR review for cluster 8): model<>() only emits *Change on child-initiated writes; it does
    // NOT emit when the parent updates the bound value. Legacy code used an @Input setter that emitted on
    // every distinct change — including parent-driven ones. The container relies on this:
    //
    //   file-browser → commitStateChange → container.commitState (plain field)
    //                                    → flows to actions via [(commitState)] → (commitStateChange)
    //                                    → container.onCommitStateChange.emit(...)
    //
    // With model<>() the final emit never fires for parent-driven updates, breaking external consumers of
    // the container's onCommitStateChange. We therefore keep an internal writable signal and synthesize
    // the *Change output via an effect that emits on every distinct change, with a sentinel to suppress
    // the initialization emit — matching the legacy setter's behavior of only emitting when the value
    // actually changed. The internal signal MUST have a different property name from the input (Angular
    // 21's two-way `[(editorState)]` binding metadata gets corrupted by a class member with the same name
    // as the input binding key — a `transformFn undefined` lookup failure occurs at runtime).
    readonly editorState = input<EditorState>(undefined!);
    readonly commitState = input<CommitState>(undefined!);
    readonly internalEditorState = signal<EditorState>(undefined!);
    readonly internalCommitState = signal<CommitState>(undefined!);
    readonly editorStateChange = output<EditorState>();
    readonly commitStateChange = output<CommitState>();

    readonly isBuildingChange = output<boolean>();
    readonly onSavedFiles = output<{
        [fileName: string]: string | undefined;
    }>();
    readonly onRefreshFiles = output();
    readonly onCommit = output<void>();
    readonly onError = output<string>();

    readonly isBuilding = signal<boolean>(false);
    readonly isResolvingConflict = signal<boolean>(false);
    readonly repositoryLink = signal<string[]>([]);
    readonly isInCourseManagement = signal<boolean>(false);

    conflictStateSubscription: Subscription;
    submissionSubscription: Subscription;
    routeParamsSubscription: Subscription;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    // Icons
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faSync = faSync;
    farPlayCircle = faPlayCircle;
    faExternalLink = faExternalLink;

    /**
     * Tracks the previous editorState so we can reproduce the legacy ngOnChanges
     * transition: when editorState transitions from SAVING to CLEAN while
     * commitState is COMMITTING, the commit completes (CLEAN); otherwise the
     * commit reverts to UNCOMMITTED_CHANGES.
     */
    private previousEditorState: EditorState | undefined;

    // Sentinels used to suppress the initial *Change emit when the input first flows in,
    // matching the legacy @Input setter's `if (value !== this._value)` dedup.
    private editorStateSeen = false;
    private commitStateSeen = false;

    constructor() {
        // Sync editorState input → internalEditorState; manually emit editorStateChange on every
        // actual change. Ignore undefined incoming values so internal mutations are not stomped when
        // the parent never binds the input (matches legacy `if (value !== this._value)` setter dedup,
        // where identical undefined writes were no-ops).
        effect(() => {
            const incoming = this.editorState();
            if (incoming === undefined) {
                return;
            }
            const current = untracked(() => this.internalEditorState());
            if (incoming === current) {
                this.editorStateSeen = true;
                return;
            }
            this.internalEditorState.set(incoming);
            if (this.editorStateSeen) {
                this.editorStateChange.emit(incoming);
            }
            this.editorStateSeen = true;
        });

        // Sync commitState input → internalCommitState; manually emit commitStateChange on every
        // actual change.
        effect(() => {
            const incoming = this.commitState();
            if (incoming === undefined) {
                return;
            }
            const current = untracked(() => this.internalCommitState());
            if (incoming === current) {
                this.commitStateSeen = true;
                return;
            }
            this.internalCommitState.set(incoming);
            if (this.commitStateSeen) {
                this.commitStateChange.emit(incoming);
            }
            this.commitStateSeen = true;
        });

        // Reproduce legacy ngOnChanges cascade: when internalEditorState transitions SAVING -> X
        // while internalCommitState is COMMITTING, finalize the commit (CLEAN if editor is now
        // CLEAN, otherwise revert to UNCOMMITTED_CHANGES). The commitState guard is evaluated
        // INSIDE the setTimeout so that a commitState change between scheduling and firing is
        // respected — matching legacy behavior where the read happened at fire time.
        effect(() => {
            const current = this.internalEditorState();
            // Snapshot the previous value before updating it for the next run.
            const previous = untracked(() => this.previousEditorState);
            this.previousEditorState = current;
            if (previous === EditorState.SAVING) {
                // Defer with setTimeout(..., 0) (macrotask) so we don't write to the
                // commitState signal inside the editorState effect's own change-detection pass,
                // and so the commitState guard reflects the value at fire time, not schedule time.
                setTimeout(() => {
                    if (untracked(() => this.internalCommitState()) !== CommitState.COMMITTING) {
                        return;
                    }
                    if (current === EditorState.CLEAN) {
                        this.setCommitState(CommitState.CLEAN);
                    } else {
                        this.setCommitState(CommitState.UNCOMMITTED_CHANGES);
                    }
                }, 0);
            }
        });
    }

    /**
     * Writes internalCommitState and emits commitStateChange. Use this for all internal mutations
     * so the parent's (commitStateChange) handler fires — model<>() was replaced precisely
     * because it didn't emit on parent-driven updates; we manage both directions explicitly.
     *
     * Public so integration tests can drive parent→child state pushes synchronously without
     * waiting for the signal-input effect microtask.
     */
    public setCommitState(next: CommitState): void {
        const current = untracked(() => this.internalCommitState());
        if (current === next) {
            return;
        }
        this.internalCommitState.set(next);
        this.commitStateChange.emit(next);
    }

    /**
     * Writes internalEditorState and emits editorStateChange. See setCommitState. Public for the
     * same integration-test reason.
     */
    public setEditorState(next: EditorState): void {
        const current = untracked(() => this.internalEditorState());
        if (current === next) {
            return;
        }
        this.internalEditorState.set(next);
        this.editorStateChange.emit(next);
    }

    ngOnInit(): void {
        this.routeParamsSubscription = this.route.params.subscribe((params) => {
            const repositoryType = params['repositoryType'] ?? 'USER';
            const courseId = Number(params['courseId']);
            const repositoryId = Number(params['repositoryId']);
            const exerciseId = Number(params['exerciseId']);
            const examId = Number(params['examId']);
            const exerciseGroupId = Number(params['exerciseGroupId']);
            this.repositoryLink.set(getLocalRepositoryLink(courseId, exerciseId, repositoryType, repositoryId, examId, exerciseGroupId));
        });
        this.isInCourseManagement.set(this.router.url.includes('course-management'));

        this.conflictStateSubscription = this.conflictService.subscribeConflictState().subscribe((gitConflictState: GitConflictState) => {
            // When the conflict is encountered when opening the code-editor, setting the commitState here could cause an uncheckedException.
            // Schedule the state change for the next tick to ensure template is rendered.
            if (this.internalCommitState() === CommitState.CONFLICT && gitConflictState === GitConflictState.OK) {
                // Case a: Conflict was resolved.
                setTimeout(() => {
                    this.setCommitState(CommitState.UNDEFINED);
                }, 0);
            } else if (this.internalCommitState() !== CommitState.CONFLICT && gitConflictState === GitConflictState.CHECKOUT_CONFLICT) {
                // Case b: Conflict has occurred.
                setTimeout(() => {
                    this.setCommitState(CommitState.CONFLICT);
                }, 0);
            }
        });
        this.submissionSubscription = this.submissionService
            .getBuildingState()
            .pipe(
                tap((isBuilding: boolean) => {
                    this.isBuilding.set(isBuilding);
                }),
            )
            .subscribe();

        if (!this.disableAutoSave()) {
            // Run interval outside Angular zone to prevent unnecessary change detection cycles
            this.ngZone.runOutsideAngular(() => {
                this.autoSaveInterval = window.setInterval(() => {
                    this.autoSaveTimer++;
                    if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                        this.autoSaveTimer = 0;
                        // Re-enter Angular zone only when we need to save
                        this.ngZone.run(() => this.onSave());
                    }
                }, AUTOSAVE_CHECK_INTERVAL);
            });
        }
    }

    ngOnDestroy(): void {
        clearInterval(this.autoSaveInterval);
        this.onSave();

        if (this.conflictStateSubscription) {
            this.conflictStateSubscription.unsubscribe();
        }
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        if (this.routeParamsSubscription) {
            this.routeParamsSubscription.unsubscribe();
        }
    }

    onRefresh() {
        if (this.internalEditorState() !== EditorState.CLEAN) {
            const ref = this.dialogService.open(CodeEditorConfirmRefreshModalComponent, {
                header: this.translateService.instant('artemisApp.editor.refresh.refreshExplanationShort'),
                width: '50rem',
                modal: true,
                closable: true,
                closeOnEscape: true,
                dismissableMask: false,
            });
            ref?.onClose.subscribe((confirmed: boolean | undefined) => {
                if (confirmed) {
                    this.executeRefresh();
                }
            });
        } else {
            this.executeRefresh();
        }
    }

    executeRefresh() {
        this.setEditorState(EditorState.REFRESHING);
        this.repositoryService.pull().subscribe({
            next: () => {
                this.onRefreshFiles.emit();
                this.setEditorState(EditorState.CLEAN);
            },
            error: (error: Error) => {
                this.setEditorState(EditorState.UNSAVED_CHANGES);
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
     * @param andCommit whether the saved changed in the files should be committed or not
     * @desc Saves all files that have unsaved changes in the editor.
     */
    saveChangedFiles(andCommit = false): Observable<any> {
        const unsavedFilesValue = this.unsavedFiles();
        if (!_isEmpty(unsavedFilesValue)) {
            this.setEditorState(EditorState.SAVING);
            const unsavedFiles = Object.entries(unsavedFilesValue).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            return this.repositoryFileService.updateFiles(unsavedFiles, andCommit).pipe(
                tap((fileSubmission: FileSubmission) => {
                    this.onSavedFiles.emit(fileSubmission);
                }),
                catchError((error: Error) => {
                    this.setEditorState(EditorState.UNSAVED_CHANGES);
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('saveFailed' + error.message);
                    } else {
                        this.onError.emit('saveFailed');
                    }
                    return throwError(() => error);
                }),
            );
        }
        return of(undefined);
    }

    /**
     * @function commit
     * @desc Commits the current repository files.
     * If there are unsaved changes, save them first before trying to commit again.
     */
    commit() {
        // Avoid multiple commits at the same time.
        if (this.internalCommitState() === CommitState.COMMITTING) {
            return;
        }
        // If there are unsaved changes, save them before trying to commit again.
        of(undefined)
            .pipe(
                tap(() => this.setCommitState(CommitState.COMMITTING)),
                switchMap(() => {
                    if (!_isEmpty(this.unsavedFiles())) {
                        return this.saveChangedFiles(true);
                    } else {
                        return this.repositoryService.commit();
                    }
                }),
                tap(() => {
                    if (this.internalEditorState() === EditorState.CLEAN) {
                        this.setCommitState(CommitState.CLEAN);
                    }
                    // We just assume that after the commit a build happens if the repo is buildable.
                    if (this.buildable()) {
                        // Note: this is not 100% clean, but not setting it here would complicate the state model.
                        this.isBuilding.set(true);
                    }
                }),
                tap(() => {
                    this.onCommit.emit();
                }),
            )
            .subscribe({
                error: (error: HttpErrorResponse) => {
                    this.setCommitState(CommitState.UNCOMMITTED_CHANGES);
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('submitFailed' + error.message);
                    } else {
                        this.onError.emit('submitFailed');
                    }

                    if (error.error.detail) {
                        const detailMessage = error.error.detail;
                        if (detailMessage.includes('submitBeforeStartDate')) {
                            this.onError.emit('submitBeforeStartDate');
                        } else if (detailMessage.includes('submitAfterDueDate')) {
                            this.onError.emit('submitAfterDueDate');
                        } else if (detailMessage.includes('submitAfterReachingSubmissionLimit')) {
                            this.onError.emit('submitAfterReachingSubmissionLimit');
                        }
                    }
                },
            });
    }

    resetRepository() {
        const ref = this.dialogService.open(CodeEditorResolveConflictModalComponent, {
            header: this.translateService.instant('artemisApp.editor.conflict.conflictExplanationShort'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
        });
        ref?.onClose.subscribe((confirmed: boolean | undefined) => {
            if (!confirmed) {
                return;
            }
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
