import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogRef, TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { catchError, concatMap, filter, finalize, map, reduce, switchMap, tap } from 'rxjs/operators';
import { EMPTY, Observable, Subject, Subscription, firstValueFrom, from, of, throwError } from 'rxjs';
import { isEmpty as _isEmpty } from 'lodash-es';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { faCircleNotch, faExternalLink, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faPlayCircle } from '@fortawesome/free-regular-svg-icons';
import { Participation, getExercise } from 'app/exercise/shared/entities/participation/participation.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { isPushOutsideBuildWindow } from 'app/programming/shared/utils/build-time-window.utils';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getLocalRepositoryLink } from 'app/foundation/util/navigation.utils';
import {
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
    ConnectionError,
    MAX_FILE_UPDATE_BATCH_SIZE_BYTES,
    checkIfSubmissionIsError,
} from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/programming/shared/code-editor/services/code-editor-conflict-state.service';
import { CodeEditorSubmissionService } from 'app/programming/shared/code-editor/services/code-editor-submission.service';
import { CommitState, EditorState, FileSubmission, FileSubmissionError, GitConflictState } from '../model/code-editor.model';
import { CodeEditorConfirmRefreshModalComponent } from 'app/programming/shared/code-editor/actions/refresh-modal/code-editor-confirm-refresh-modal.component';
import { CodeEditorResolveConflictModalComponent } from 'app/programming/shared/code-editor/actions/conflict-modal/code-editor-resolve-conflict-modal.component';

type FileUpdate = { fileName: string; fileContent: string };

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

@Component({
    selector: 'jhi-code-editor-actions',
    templateUrl: './code-editor-actions.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumAetUiButtonDirective, FeatureToggleDirective, TumAetUiTooltipDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, RouterLink],
})
export class CodeEditorActionsComponent implements OnInit, OnDestroy {
    private readonly repositoryService = inject(CodeEditorRepositoryService);
    private readonly repositoryFileService = inject(CodeEditorRepositoryFileService);
    private readonly conflictService = inject(CodeEditorConflictStateService);
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly translateService = inject(TranslateService);
    private readonly submissionService = inject(CodeEditorSubmissionService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly alertService = inject(AlertService);

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
        [fileName: string]: string | null | undefined;
    }>();
    readonly onRefreshFiles = output<void>();
    readonly onCommit = output<void>();
    readonly onError = output<string>();

    readonly isBuilding = signal<boolean>(false);
    readonly isResolvingConflict = signal<boolean>(false);
    readonly repositoryLink = signal<string[]>([]);
    readonly isInCourseManagement = signal<boolean>(false);

    conflictStateSubscription?: Subscription;
    submissionSubscription?: Subscription;
    routeParamsSubscription?: Subscription;

    autoSaveTimer = 0;
    autoSaveInterval?: number;

    private refreshModalRef?: TumAetUiDialogRef<boolean, CodeEditorConfirmRefreshModalComponent>;
    private conflictModalRef?: TumAetUiDialogRef<boolean, CodeEditorResolveConflictModalComponent>;

    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faSync = faSync;
    farPlayCircle = faPlayCircle;
    faExternalLink = faExternalLink;

    private previousEditorState: EditorState | undefined;
    private saveInProgress = false;
    private saveFinished = new Subject<boolean>();
    private destroyed = false;
    private saveOnDestroy?: () => void;

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
            // The page of the code of a student has the id of the participation instead of the one of a repository
            const repositoryId = Number(params['repositoryId'] ?? params['participationId']);
            const exerciseId = Number(params['exerciseId']);
            const examId = Number(params['examId']);
            const exerciseGroupId = Number(params['exerciseGroupId']);
            this.repositoryLink.set(getLocalRepositoryLink(courseId, exerciseId, repositoryType, repositoryId, exerciseGroupId, examId));
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
            this.autoSaveInterval = window.setInterval(() => {
                this.autoSaveTimer++;
                if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                    this.autoSaveTimer = 0;
                    this.onSave();
                }
            }, AUTOSAVE_CHECK_INTERVAL);
        }
    }

    ngOnDestroy(): void {
        clearInterval(this.autoSaveInterval);
        this.destroyed = true;
        if (!this.disableActions()) {
            const files = this.unsavedFiles();
            const repositoryTarget = this.repositoryFileService.getRestResourceUrl();
            const save = () =>
                this.saveFiles(files, repositoryTarget)
                    .pipe(catchError(() => EMPTY))
                    .subscribe();
            if (this.saveInProgress) {
                // Inputs stop updating after destruction; retain the final snapshot and its repository.
                this.saveOnDestroy = save;
            } else {
                save();
            }
        }

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
        if (this.disableActions()) {
            return;
        }
        if (this.editorState() !== EditorState.CLEAN) {
            this.refreshModalRef =
                this.dialogService.open<CodeEditorConfirmRefreshModalComponent, boolean>(CodeEditorConfirmRefreshModalComponent, {
                    header: this.translateService.instant('artemisApp.editor.refresh.refreshExplanationShort'),
                    size: 'large',
                    closeOnEscape: true,
                    dismissableMask: false,
                }) ?? undefined;
            this.refreshModalRef?.onClose.subscribe((confirmed: boolean | undefined) => {
                if (confirmed && !this.disableActions()) {
                    this.executeRefresh().subscribe();
                }
            });
        } else {
            this.executeRefresh().subscribe();
        }
    }

    executeRefresh(): Observable<boolean> {
        if (this.disableActions()) {
            return EMPTY;
        }
        return this.refreshRepository();
    }

    private refreshRepository(): Observable<boolean> {
        this.editorState.set(EditorState.REFRESHING);
        return this.repositoryService.pull().pipe(
            map(() => {
                this.onRefreshFiles.emit();
                this.editorState.set(EditorState.CLEAN);
                return true;
            }),
            catchError((error: Error) => {
                this.editorState.set(EditorState.UNSAVED_CHANGES);
                if (error.message === ConnectionError.message) {
                    this.onError.emit('refreshFailed' + error.message);
                } else {
                    this.onError.emit('refreshFailed');
                }
                return of(false);
            }),
        );
    }

    onSave() {
        if (this.disableActions()) {
            return;
        }
        this.saveChangedFiles()
            .pipe(catchError(() => of()))
            .subscribe();
    }

    async saveBeforeSwitching(): Promise<void> {
        while (this.saveInProgress || !_isEmpty(this.unsavedFiles())) {
            if (this.saveInProgress) {
                if (!(await firstValueFrom(this.saveFinished))) {
                    throw new Error('The pending save failed');
                }
            } else {
                await firstValueFrom(this.saveChangedFiles());
            }
        }
    }

    saveChangedFiles(andCommit = false): Observable<FileSubmission | FileSubmissionError | undefined> {
        if (this.disableActions() || this.saveInProgress) {
            return EMPTY;
        }
        return this.saveFiles(this.unsavedFiles(), this.repositoryFileService.getRestResourceUrl(), andCommit);
    }

    private saveFiles(unsavedFilesValue: Record<string, string>, repositoryTarget: string | undefined, andCommit = false): Observable<FileSubmission | undefined> {
        if (!_isEmpty(unsavedFilesValue)) {
            if (!this.destroyed) {
                this.editorState.set(EditorState.SAVING);
            }
            const unsavedFiles = Object.entries(unsavedFilesValue).map(([fileName, fileContent]) => ({ fileName, fileContent }));
            const combinedSubmission: FileSubmission = Object.create(null);
            const validFiles = unsavedFiles.filter(({ fileName, fileContent }) => {
                if (new TextEncoder().encode(fileContent).length > MAX_FILE_SIZE_BYTES) {
                    combinedSubmission[fileName] = `The file exceeds the size limit of ${MAX_FILE_SIZE_BYTES} bytes.`;
                    return false;
                }
                return true;
            });
            const hasOversizedFiles = !_isEmpty(combinedSubmission);
            const fileUpdateBatches = this.createFileUpdateBatches(validFiles);
            let previousBatchReportedError = false;
            let saveSucceeded = false;
            let resultsReported = false;
            const reportResults = (fileSubmission: FileSubmission) => {
                resultsReported = true;
                if (this.destroyed) {
                    return;
                }
                const completedFiles = Object.fromEntries(
                    Object.entries(fileSubmission).filter(([fileName, error]) => error || this.unsavedFiles()[fileName] === unsavedFilesValue[fileName]),
                );
                if (!_isEmpty(completedFiles)) {
                    this.onSavedFiles.emit(completedFiles);
                }
            };
            const saveFinished = (this.saveFinished = new Subject<boolean>());
            this.saveInProgress = true;
            const saveOperation = from(fileUpdateBatches).pipe(
                concatMap((fileUpdateBatch, index) => {
                    if (previousBatchReportedError) {
                        return EMPTY;
                    }
                    return this.saveFileUpdateBatch(fileUpdateBatch, andCommit && !hasOversizedFiles && index === fileUpdateBatches.length - 1, repositoryTarget).pipe(
                        tap((submission) => {
                            previousBatchReportedError = Object.values(submission).some((error) => !!error);
                        }),
                    );
                }),
                reduce((submission, batchSubmission) => {
                    for (const [fileName, error] of Object.entries(batchSubmission)) {
                        submission[fileName] = error;
                    }
                    return submission;
                }, combinedSubmission),
                filter(() => this.repositoryFileService.getRestResourceUrl() === repositoryTarget),
                tap((fileSubmission: FileSubmission) => {
                    reportResults(fileSubmission);
                    if (previousBatchReportedError || hasOversizedFiles) {
                        throw new Error('At least one file could not be saved');
                    }
                    saveSucceeded = true;
                }),
                catchError((error: Error) => {
                    if (this.destroyed || this.repositoryFileService.getRestResourceUrl() !== repositoryTarget) {
                        return EMPTY;
                    }
                    if (!resultsReported) {
                        reportResults(combinedSubmission);
                    }
                    this.editorState.set(EditorState.UNSAVED_CHANGES);
                    if (error.message === ConnectionError.message) {
                        this.onError.emit('saveFailed' + error.message);
                    } else {
                        this.onError.emit('saveFailed');
                    }
                    return throwError(() => error);
                }),
            );
            return this.repositoryFileService
                .runExclusive(() => saveOperation)
                .pipe(
                    finalize(() => {
                        this.saveInProgress = false;
                        saveFinished.next(saveSucceeded);
                        saveFinished.complete();
                        const saveOnDestroy = this.saveOnDestroy;
                        this.saveOnDestroy = undefined;
                        saveOnDestroy?.();
                    }),
                );
        }
        return of(undefined);
    }

    private saveFileUpdateBatch(fileUpdateBatch: FileUpdate[], andCommit: boolean, repositoryTarget: string | undefined): Observable<FileSubmission> {
        return this.repositoryFileService.updateFiles(fileUpdateBatch, andCommit, repositoryTarget).pipe(
            map((fileSubmission) => {
                if (checkIfSubmissionIsError(fileSubmission)) {
                    throw new Error(fileSubmission.error);
                }
                return fileSubmission;
            }),
        );
    }

    private createFileUpdateBatches(fileUpdates: FileUpdate[]): FileUpdate[][] {
        const encoder = new TextEncoder();
        const batches: FileUpdate[][] = [];
        let currentBatch: FileUpdate[] = [];
        let currentBatchSize = 2; // JSON array brackets

        for (const fileUpdate of fileUpdates) {
            const serializedUpdateSize = encoder.encode(JSON.stringify(fileUpdate)).length;
            const separatorSize = currentBatch.length > 0 ? 1 : 0;
            if (currentBatch.length > 0 && currentBatchSize + separatorSize + serializedUpdateSize >= MAX_FILE_UPDATE_BATCH_SIZE_BYTES) {
                batches.push(currentBatch);
                currentBatch = [];
                currentBatchSize = 2;
            }
            currentBatch.push(fileUpdate);
            currentBatchSize += (currentBatch.length > 1 ? 1 : 0) + serializedUpdateSize;
        }

        if (currentBatch.length > 0) {
            batches.push(currentBatch);
        }
        return batches;
    }

    commit() {
        if (this.disableActions() || this.saveInProgress || this.commitState() === CommitState.COMMITTING) {
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
                    const participation = this.participation();
                    if (isPushOutsideBuildWindow(participation && getExercise(participation), participation)) {
                        // The server saved the commit without building it, so no build follows.
                        this.alertService.info('artemisApp.editor.committedOutsideBuildWindow');
                    } else if (this.buildable()) {
                        // Optimistic: assume a build follows the commit on a buildable repo.
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

                    if (error.error?.detail) {
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
        if (this.disableActions() || this.isResolvingConflict()) {
            return;
        }
        this.conflictModalRef =
            this.dialogService.open<CodeEditorResolveConflictModalComponent, boolean>(CodeEditorResolveConflictModalComponent, {
                header: this.translateService.instant('artemisApp.editor.conflict.conflictExplanationShort'),
                size: 'large',
                closeOnEscape: true,
                dismissableMask: false,
            }) ?? undefined;
        this.conflictModalRef?.onClose.subscribe((confirmed: boolean | undefined) => {
            if (!confirmed || this.disableActions() || this.isResolvingConflict()) {
                return;
            }
            this.isResolvingConflict.set(true);
            this.repositoryService
                .resetRepository()
                .pipe(finalize(() => this.isResolvingConflict.set(false)))
                .subscribe({
                    next: () => {
                        this.conflictService.notifyConflictState(GitConflictState.OK);
                        this.executeRefresh().subscribe();
                    },
                    error: () => {
                        this.onError.emit('resetFailed');
                    },
                });
        });
    }
}
