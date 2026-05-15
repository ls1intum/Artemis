import { ChangeDetectionStrategy, Component, NgZone, OnDestroy, OnInit, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
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
    readonly unsavedFiles = input.required<{
        [fileName: string]: string;
    }>();
    readonly disableActions = input(false);
    readonly disableAutoSave = input(false);
    readonly participation = input<Participation>();

    readonly editorState = model.required<EditorState>();
    readonly commitState = model.required<CommitState>();

    readonly isBuildingChange = output<boolean>();
    readonly onSavedFiles = output<{
        [fileName: string]: string | undefined;
    }>();
    readonly onRefreshFiles = output<void>();
    readonly onCommit = output<void>();
    readonly onError = output<string>();

    readonly isBuilding = signal<boolean>(false);
    readonly isResolvingConflict = signal<boolean>(false);
    readonly repositoryLink = signal<string[]>([]);
    readonly isInCourseManagement = signal<boolean>(false);

    conflictStateSubscription: Subscription;
    submissionSubscription: Subscription;
    routeParamsSubscription: Subscription;

    autoSaveTimer = 0;
    autoSaveInterval: number;

    private refreshModalRef?: DynamicDialogRef;
    private conflictModalRef?: DynamicDialogRef;

    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faSync = faSync;
    farPlayCircle = faPlayCircle;
    faExternalLink = faExternalLink;

    private previousEditorState: EditorState | undefined;

    constructor() {
        effect(() => {
            const current = this.editorState();
            const previous = untracked(() => this.previousEditorState);
            this.previousEditorState = current;
            if (previous === EditorState.SAVING) {
                // setTimeout(0): defer the commitState write outside this effect's CD pass and
                // re-read editorState/commitState at fire time so a concurrent change is respected.
                // If another edit flips editorState back to UNSAVED_CHANGES before the macrotask
                // fires, we must NOT clobber it with CLEAN based on the stale snapshot.
                setTimeout(() => {
                    if (untracked(() => this.commitState()) !== CommitState.COMMITTING) {
                        return;
                    }
                    const editorStateAtFire = untracked(() => this.editorState());
                    if (editorStateAtFire === EditorState.CLEAN) {
                        this.commitState.set(CommitState.CLEAN);
                    } else {
                        this.commitState.set(CommitState.UNCOMMITTED_CHANGES);
                    }
                }, 0);
            }
        });
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
            // setTimeout(0): the conflict signal can arrive during initial code-editor render;
            // writing commitState synchronously here throws ExpressionChangedAfterItHasBeenCheckedError.
            if (this.commitState() === CommitState.CONFLICT && gitConflictState === GitConflictState.OK) {
                setTimeout(() => {
                    this.commitState.set(CommitState.UNDEFINED);
                }, 0);
            } else if (this.commitState() !== CommitState.CONFLICT && gitConflictState === GitConflictState.CHECKOUT_CONFLICT) {
                setTimeout(() => {
                    this.commitState.set(CommitState.CONFLICT);
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
            // Tick outside the Angular zone to avoid waking change detection every interval;
            // re-enter only on the rare save tick.
            this.ngZone.runOutsideAngular(() => {
                this.autoSaveInterval = window.setInterval(() => {
                    this.autoSaveTimer++;
                    if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                        this.autoSaveTimer = 0;
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
        this.refreshModalRef?.close();
        this.conflictModalRef?.close();
    }

    onRefresh() {
        if (this.editorState() !== EditorState.CLEAN) {
            this.refreshModalRef =
                this.dialogService.open(CodeEditorConfirmRefreshModalComponent, {
                    header: this.translateService.instant('artemisApp.editor.refresh.refreshExplanationShort'),
                    width: '50rem',
                    modal: true,
                    closable: true,
                    closeOnEscape: true,
                    dismissableMask: false,
                }) ?? undefined;
            this.refreshModalRef?.onClose.subscribe((confirmed: boolean | undefined) => {
                if (confirmed) {
                    this.executeRefresh();
                }
            });
        } else {
            this.executeRefresh();
        }
    }

    executeRefresh() {
        this.editorState.set(EditorState.REFRESHING);
        this.repositoryService.pull().subscribe({
            next: () => {
                this.onRefreshFiles.emit();
                this.editorState.set(EditorState.CLEAN);
            },
            error: (error: Error) => {
                this.editorState.set(EditorState.UNSAVED_CHANGES);
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

    saveChangedFiles(andCommit = false): Observable<any> {
        const unsavedFilesValue = this.unsavedFiles();
        if (!_isEmpty(unsavedFilesValue)) {
            this.editorState.set(EditorState.SAVING);
            const unsavedFiles = Object.entries(unsavedFilesValue).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            return this.repositoryFileService.updateFiles(unsavedFiles, andCommit).pipe(
                tap((fileSubmission: FileSubmission) => {
                    this.onSavedFiles.emit(fileSubmission);
                }),
                catchError((error: Error) => {
                    this.editorState.set(EditorState.UNSAVED_CHANGES);
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

    commit() {
        if (this.commitState() === CommitState.COMMITTING) {
            return;
        }
        of(undefined)
            .pipe(
                tap(() => this.commitState.set(CommitState.COMMITTING)),
                switchMap(() => {
                    if (!_isEmpty(this.unsavedFiles())) {
                        return this.saveChangedFiles(true);
                    } else {
                        return this.repositoryService.commit();
                    }
                }),
                tap(() => {
                    if (this.editorState() === EditorState.CLEAN) {
                        this.commitState.set(CommitState.CLEAN);
                    }
                    // Optimistic: assume a build follows the commit on a buildable repo.
                    if (this.buildable()) {
                        this.isBuilding.set(true);
                    }
                }),
                tap(() => {
                    this.onCommit.emit();
                }),
            )
            .subscribe({
                error: (error: HttpErrorResponse) => {
                    this.commitState.set(CommitState.UNCOMMITTED_CHANGES);
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
        this.conflictModalRef =
            this.dialogService.open(CodeEditorResolveConflictModalComponent, {
                header: this.translateService.instant('artemisApp.editor.conflict.conflictExplanationShort'),
                width: '50rem',
                modal: true,
                closable: true,
                closeOnEscape: true,
                dismissableMask: false,
            }) ?? undefined;
        this.conflictModalRef?.onClose.subscribe((confirmed: boolean | undefined) => {
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
