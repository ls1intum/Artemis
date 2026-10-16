import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { Subscription, catchError, finalize, of, take } from 'rxjs';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiPopoverComponent,
    TumAetUiPopoverTriggerDirective,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { HyperionCodeGenerationApi } from 'app/openapi/api/hyperion-code-generation-api';
import { CodeGenerationJobStartRepositoryTypeEnum } from 'app/openapi/model/code-generation-job-start';
import { CodeGenerationRequest } from 'app/openapi/model/code-generation-request';
import { HyperionCompletionStatus, HyperionEvent, HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CommentThread, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';

const SUPPORTED_CODE_GENERATION_REPOSITORIES = [RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS] as const;
const CODE_GENERATION_SLOT_RELEASE_POLL_INTERVAL_MS = 1000;
const CODE_GENERATION_SLOT_RELEASE_MAX_POLLS = 120;
const CODE_GENERATION_FILE_PULL_DEBOUNCE_MS = 250;
const CODE_GENERATION_STATUS_SESSION_STORAGE_KEY_PREFIX = 'programming-exercise.code-generation.status.';
const CODE_GENERATION_STATUS_SESSION_STORAGE_TTL_MS = 3_600_000;
type SupportedCodeGenerationRepositoryType = (typeof SUPPORTED_CODE_GENERATION_REPOSITORIES)[number];
type CodeGenerationExecutionState = 'idle' | 'queued' | 'running' | 'success' | 'warning' | 'error' | 'skipped' | 'unknown';
type CodeGenerationFileEventType = 'FILE_UPDATED' | 'NEW_FILE' | 'FILE_DELETED';
interface CodeGenerationFileActivity {
    repositoryType: SupportedCodeGenerationRepositoryType;
    eventType: CodeGenerationFileEventType;
    path: string;
    iteration?: number;
    timestamp: number;
}
interface CodeGenerationRepositoryStatus {
    repositoryType: SupportedCodeGenerationRepositoryType;
    enabled: boolean;
    state: CodeGenerationExecutionState;
    attempts?: number;
    message?: string;
    fileActivities: CodeGenerationFileActivity[];
}
interface PersistedCodeGenerationState {
    updatedAt: number;
    statuses: CodeGenerationRepositoryStatus[];
    queuedRepositories: SupportedCodeGenerationRepositoryType[];
    activeRepository?: SupportedCodeGenerationRepositoryType;
    initialAutoGeneration: boolean;
}

/** Existing per-repository generation, retained for exercises outside the verified whole-exercise workflow. */
@Component({
    selector: 'jhi-legacy-code-generation',
    templateUrl: './legacy-code-generation.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [TumAetUiConfirmationService],
    imports: [
        TumAetUiCheckboxComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
    ],
})
export class LegacyCodeGenerationComponent implements OnDestroy {
    private readonly hyperionCodeGenerationApi = inject(HyperionCodeGenerationApi);
    private readonly hyperionWs = inject(HyperionWebsocketService);
    private readonly websocket = inject(WebsocketService);
    private readonly repoService = inject(CodeEditorRepositoryService);
    private readonly exerciseReviewCommentService = inject(ExerciseReviewCommentService);
    private readonly codeGenAlertService = inject(AlertService);
    private readonly sessionStorageService = inject(SessionStorageService);
    private readonly translateService = inject(TranslateService);
    private readonly confirmations = inject(TumAetUiConfirmationService);
    private readonly destroyRef = inject(DestroyRef);
    readonly exercise = input.required<ProgrammingExercise>();
    readonly selectedRepository = input.required<RepositoryType>();
    readonly blockedReason = input<string | undefined>();
    readonly refreshRequested = output<void>();
    readonly feedbackRequested = output<ReviewThreadLocation>();
    readonly runningChanged = output<boolean>();
    readonly isGeneratingCode = signal(false);
    readonly controlsBlocked = computed(() => this.isGeneratingCode() || !!this.blockedReason());
    private readonly settingsPopover = viewChild<TumAetUiPopoverComponent>('settings');
    private jobSubscription?: Subscription;
    private jobTimeoutHandle?: number;
    private activeJobId?: string;
    private statusSubscription?: Subscription;
    private startSubscription?: Subscription;
    private restoreRequestId = 0;
    private startPending = false;
    private restoreAfterStart = false;
    private slotReleasePollTimeoutHandle?: number;
    private codeGenerationPullTimeoutHandles = new Map<SupportedCodeGenerationRepositoryType, number>();
    private codeGenerationPullSubscriptions = new Map<SupportedCodeGenerationRepositoryType, Subscription>();
    private repositoriesWithPendingCodeGenerationPull = new Set<SupportedCodeGenerationRepositoryType>();
    private repositoriesWithInFlightCodeGenerationPull = new Set<SupportedCodeGenerationRepositoryType>();
    private queuedCodeGenerationRepositories: SupportedCodeGenerationRepositoryType[] = [];
    private activeCodeGenerationRepository?: SupportedCodeGenerationRepositoryType;
    private currentCodeGenerationUsesInitialIterationLimit = false;
    private hasCustomCodeGenerationSelection = false;
    private contextExerciseId?: number;
    readonly codeGenerationStatuses = signal<CodeGenerationRepositoryStatus[]>(SUPPORTED_CODE_GENERATION_REPOSITORIES.map((type) => this.createCodeGenerationStatus(type)));
    readonly displayedStatuses = computed(() =>
        this.codeGenerationStatuses().map((status) => {
            const feedbackIds = new Set(this.exerciseReviewCommentService.getSelectedFeedbackThreadIdsForRepository(status.repositoryType));
            return cloneWith(status, {
                labelKey: `artemisApp.programmingExercise.codeGeneration.repositories.${status.repositoryType.toLowerCase()}`,
                stateKey: `artemisApp.programmingExercise.codeGeneration.status.${status.state}`,
                feedbackCount: feedbackIds.size,
                feedbackThreads: (feedbackIds.size ? this.exerciseReviewCommentService.threads() : [])
                    .filter((thread) => feedbackIds.has(thread.id))
                    .map((thread) => {
                        const filePath = thread.filePath ?? thread.initialFilePath ?? undefined;
                        const lineNumber = thread.lineNumber ?? thread.initialLineNumber;
                        return cloneWith(thread, {
                            locationLabel: filePath
                                ? (lineNumber ?? 0) > 0
                                    ? `${filePath}:${lineNumber}`
                                    : filePath
                                : this.translateService.instant(
                                      (lineNumber ?? 0) > 0
                                          ? 'artemisApp.programmingExercise.codeGeneration.selectedFeedback.line'
                                          : 'artemisApp.programmingExercise.codeGeneration.selectedFeedback.unknownLocation',
                                      { line: lineNumber },
                                  ),
                        });
                    }),
                fileActivities: status.fileActivities.map((activity) =>
                    cloneWith(activity, {
                        actionKey: `artemisApp.programmingExercise.codeGeneration.status.${activity.eventType === 'NEW_FILE' ? 'fileCreated' : activity.eventType === 'FILE_DELETED' ? 'fileDeleted' : 'fileUpdated'}`,
                    }),
                ),
            });
        }),
    );

    constructor() {
        effect(() => {
            const exerciseId = this.exercise().id;
            const blockedReason = this.blockedReason();
            this.selectedRepository();
            untracked(() => {
                if (exerciseId !== this.contextExerciseId) {
                    this.stopObserving();
                    this.contextExerciseId = exerciseId;
                    this.hasCustomCodeGenerationSelection = false;
                    this.codeGenerationStatuses.set(SUPPORTED_CODE_GENERATION_REPOSITORIES.map((type) => this.createCodeGenerationStatus(type)));
                }
                if (!this.hasCustomCodeGenerationSelection && !this.isGeneratingCode()) this.syncCodeGenerationSelectionWithSelectedRepository();
                if (!blockedReason && this.isGeneratingCode() && !this.activeCodeGenerationRepository && this.queuedCodeGenerationRepositories.length) {
                    this.waitForCodeGenerationSlotRelease();
                } else if (window.history.state?.autoStartCodeGenerationAllRepositories && !blockedReason && !this.isGeneratingCode()) {
                    const state = cloneWith(window.history.state, { autoStartCodeGenerationAllRepositories: false });
                    window.history.replaceState(state, '');
                    this.startCodeGeneration([...SUPPORTED_CODE_GENERATION_REPOSITORIES], true);
                } else {
                    this.restoreCodeGenerationState();
                }
            });
        });
        effect(() => this.runningChanged.emit(this.isGeneratingCode()));
        this.websocket.connectionState.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((state) => {
            if (state.connected && this.contextExerciseId) {
                if (this.startPending) {
                    this.restoreAfterStart = true;
                    return;
                }
                if (this.isGeneratingCode() && !this.activeCodeGenerationRepository && this.queuedCodeGenerationRepositories.length) this.waitForCodeGenerationSlotRelease();
                else this.restoreCodeGenerationState(true);
            }
        });
    }

    generateCode(): void {
        if (!this.exercise().id || this.blockedReason() || this.isGeneratingCode()) return;
        const exerciseId = this.exercise().id;
        const repositories = this.getSelectedCodeGenerationRepositories();
        if (!repositories.length) {
            this.codeGenAlertService.warning('artemisApp.programmingExercise.codeGeneration.noRepositorySelected');
            return;
        }
        this.confirmations.confirm({
            header: this.translateService.instant('artemisApp.programmingExercise.codeGeneration.confirmTitle'),
            message: this.translateService.instant('artemisApp.programmingExercise.codeGeneration.confirmText'),
            acceptLabel: this.translateService.instant('entity.action.confirm'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            accept: () => {
                if (this.exercise().id === exerciseId && !this.blockedReason() && !this.isGeneratingCode()) this.startCodeGeneration(repositories);
            },
        });
    }

    navigateToFeedback(thread: CommentThread): void {
        this.settingsPopover()?.close();
        this.feedbackRequested.emit({
            threadId: thread.id,
            targetType: thread.targetType,
            auxiliaryRepositoryId: thread.auxiliaryRepositoryId,
            filePath: thread.filePath ?? thread.initialFilePath ?? undefined,
            lineNumber: thread.lineNumber ?? thread.initialLineNumber,
        });
    }

    ngOnDestroy(): void {
        this.stopObserving();
        this.runningChanged.emit(false);
    }

    private stopObserving(): void {
        this.restoreRequestId += 1;
        this.confirmations.close(undefined);
        this.startSubscription?.unsubscribe();
        this.startSubscription = undefined;
        this.startPending = false;
        this.restoreAfterStart = false;
        this.clearJobSubscription(true);
        this.clearCodeGenerationStatusSubscription();
        this.clearSlotReleasePoll();
        this.queuedCodeGenerationRepositories = [];
        this.activeCodeGenerationRepository = undefined;
    }

    private startCodeGeneration(repositories: SupportedCodeGenerationRepositoryType[], initialAutoGeneration = false) {
        this.isGeneratingCode.set(true);
        this.currentCodeGenerationUsesInitialIterationLimit = initialAutoGeneration;
        this.restoreRequestId += 1;
        this.clearCodeGenerationStatusSubscription();
        this.clearSlotReleasePoll();
        this.settingsPopover()?.close();
        this.confirmations.close(undefined);
        this.initializeCodeGenerationRunStatuses(repositories);
        this.queuedCodeGenerationRepositories = [...repositories];
        this.activeCodeGenerationRepository = undefined;
        this.persistCodeGenerationState();
        this.runNextCodeGeneration();
    }

    private runNextCodeGeneration() {
        if (this.startPending || this.blockedReason()) return;
        const repositoryType = this.queuedCodeGenerationRepositories.shift();
        const exerciseId = this.exercise().id;
        if (!repositoryType || !exerciseId) {
            this.activeCodeGenerationRepository = undefined;
            this.clearJobSubscription(true);
            return;
        }

        this.activeCodeGenerationRepository = repositoryType;
        this.updateCodeGenerationStatus(repositoryType, (status) => cloneWith(status, { state: 'running', attempts: undefined, message: undefined }));

        const request = this.createCodeGenerationRequest(repositoryType, this.currentCodeGenerationUsesInitialIterationLimit);
        const requestId = this.restoreRequestId;
        this.startPending = true;
        this.startSubscription = this.hyperionCodeGenerationApi
            .generateCode(exerciseId, request)
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => {
                    if (requestId !== this.restoreRequestId) return;
                    this.startPending = false;
                    const restore = this.restoreAfterStart;
                    this.restoreAfterStart = false;
                    if (restore && this.isGeneratingCode()) this.restoreCodeGenerationState(true);
                    else if (this.isGeneratingCode() && !this.activeCodeGenerationRepository && this.queuedCodeGenerationRepositories.length)
                        this.waitForCodeGenerationSlotRelease();
                }),
            )
            .subscribe({
                next: (res) => {
                    if (requestId !== this.restoreRequestId) return;
                    if (!res?.jobId) {
                        this.stopCodeGenerationQueue(repositoryType, undefined, 'artemisApp.programmingExercise.codeGeneration.error');
                        return;
                    }
                    this.subscribeToJob(res.jobId, repositoryType);
                },
                error: (error: HttpErrorResponse) => {
                    if (requestId !== this.restoreRequestId) return;
                    if (this.isCodeGenerationAlreadyRunning(error)) {
                        this.stopCodeGenerationQueue(
                            repositoryType,
                            this.translateService.instant('artemisApp.programmingExercise.codeGeneration.runningText'),
                            'artemisApp.programmingExercise.codeGeneration.error',
                            false,
                        );
                        this.codeGenAlertService.warning('artemisApp.programmingExercise.codeGeneration.runningText');
                        return;
                    }
                    this.stopCodeGenerationQueue(repositoryType, undefined, 'artemisApp.programmingExercise.codeGeneration.error');
                },
            });
    }

    private isCodeGenerationAlreadyRunning(error: HttpErrorResponse): boolean {
        if (!error || error.status !== 409) {
            return false;
        }
        const payload = typeof error.error === 'object' && error.error !== null ? (error.error as Record<string, unknown>) : {};
        const errorKey =
            payload['errorKey'] ?? payload['X-artemisApp-error'] ?? payload['message'] ?? error.headers?.get('X-artemisApp-error') ?? error.headers?.get('X-artemisApp-message');
        return errorKey === 'codeGenerationRunning' || errorKey === 'error.codeGenerationRunning';
    }

    private restoreCodeGenerationState(force = false) {
        if (this.startPending) {
            this.restoreAfterStart ||= force;
            return;
        }
        const exerciseId = this.exercise().id;
        if (!exerciseId) {
            return;
        }
        if (this.isGeneratingCode() && !force) {
            return;
        }
        this.restoreRequestId += 1;
        this.clearCodeGenerationStatusSubscription();
        const requestId = this.restoreRequestId;
        let persistedState = this.loadPersistedCodeGenerationState();
        this.statusSubscription = this.hyperionCodeGenerationApi.getActiveCodeGenerationJob(exerciseId).subscribe({
            next: (res) => {
                if (requestId !== this.restoreRequestId) {
                    return;
                }
                if (!res?.jobId && persistedState?.activeRepository) {
                    // The active-job endpoint has no terminal replay: a missing job does not prove success or failure.
                    const activeRepository = persistedState.activeRepository;
                    persistedState = cloneWith(persistedState, {
                        statuses: persistedState.statuses.map((status) => (status.repositoryType === activeRepository ? cloneWith(status, { state: 'unknown' as const }) : status)),
                        activeRepository: undefined,
                    });
                    this.codeGenerationStatuses.set(persistedState.statuses);
                }
                if (res?.jobId) {
                    const repositoryType = res.repositoryType ? this.mapRepositoryTypeToCodeGenerationRequest(res.repositoryType) : persistedState?.activeRepository;
                    if (!repositoryType) {
                        this.clearPersistedCodeGenerationState();
                        this.clearJobSubscription(true);
                        return;
                    }
                    this.restorePersistedCodeGenerationStatuses(repositoryType, persistedState);
                    this.subscribeToJob(res.jobId, repositoryType);
                } else if (persistedState?.queuedRepositories.length) {
                    this.restorePersistedCodeGenerationQueue(persistedState);
                    this.clearCodeGenerationStatusSubscription();
                    this.clearSlotReleasePoll();
                    this.runNextCodeGeneration();
                } else {
                    this.clearPersistedCodeGenerationState();
                    this.clearJobSubscription(true);
                }
            },
            error: () => {
                if (requestId !== this.restoreRequestId) {
                    return;
                }
                this.clearPersistedCodeGenerationState();
                this.clearJobSubscription(true);
            },
        });
    }

    private mapRepositoryTypeToCodeGenerationRequest(repositoryType: RepositoryType | CodeGenerationJobStartRepositoryTypeEnum): SupportedCodeGenerationRepositoryType | undefined {
        switch (repositoryType) {
            case RepositoryType.TEMPLATE:
                return RepositoryType.TEMPLATE;
            case RepositoryType.SOLUTION:
                return RepositoryType.SOLUTION;
            case RepositoryType.TESTS:
                return RepositoryType.TESTS;
            default:
                return undefined;
        }
    }

    private createCodeGenerationRequest(repositoryType: SupportedCodeGenerationRepositoryType, initialAutoGeneration = false): CodeGenerationRequest {
        const request: CodeGenerationRequest = { repositoryType };
        if (initialAutoGeneration) {
            request.initialAutoGeneration = true;
        }
        const selectedFeedbackThreadIds = this.exerciseReviewCommentService.getSelectedFeedbackThreadIdsForRepository(repositoryType);
        if (selectedFeedbackThreadIds.length > 0) {
            request.selectedFeedbackThreadIds = selectedFeedbackThreadIds;
        }

        return request;
    }

    private subscribeToJob(jobId: string, repositoryType: SupportedCodeGenerationRepositoryType) {
        if (this.activeJobId === jobId && this.jobSubscription) {
            return;
        }
        this.clearJobSubscription(false);
        this.activeJobId = jobId;

        this.isGeneratingCode.set(true);
        this.jobSubscription = this.hyperionWs.subscribeToJob(jobId).subscribe({
            next: (event) => this.handleCodeGenerationJobEvent(repositoryType, event),
            error: () => this.stopCodeGenerationQueue(repositoryType, undefined, 'artemisApp.programmingExercise.codeGeneration.error'),
        });

        this.jobTimeoutHandle = window.setTimeout(() => {
            if (this.isGeneratingCode() && this.activeCodeGenerationRepository === repositoryType) {
                this.stopCodeGenerationQueue(
                    repositoryType,
                    this.translateService.instant('artemisApp.programmingExercise.codeGeneration.timeoutDetails'),
                    'artemisApp.programmingExercise.codeGeneration.timeout',
                );
            }
        }, 1_200_000);
    }

    setCodeGenerationRepositoryEnabled(repositoryType: SupportedCodeGenerationRepositoryType, enabled: boolean): void {
        if (this.isGeneratingCode()) {
            return;
        }

        this.hasCustomCodeGenerationSelection = true;
        this.updateCodeGenerationStatus(repositoryType, (status) => cloneWith(status, { enabled }));
    }

    private clearJobSubscription(stopSpinner: boolean) {
        if (stopSpinner) {
            this.clearCodeGenerationRepositoryPulls();
            this.isGeneratingCode.set(false);
            this.currentCodeGenerationUsesInitialIterationLimit = false;
        }
        if (this.activeJobId) {
            this.hyperionWs.unsubscribeFromJob(this.activeJobId);
            this.activeJobId = undefined;
        }
        this.jobSubscription?.unsubscribe();
        this.jobSubscription = undefined;
        if (this.jobTimeoutHandle) {
            clearTimeout(this.jobTimeoutHandle);
            this.jobTimeoutHandle = undefined;
        }
    }

    private handleCodeGenerationJobEvent(repositoryType: SupportedCodeGenerationRepositoryType, event: HyperionEvent) {
        if (event.type === 'FILE_UPDATED' || event.type === 'NEW_FILE' || event.type === 'FILE_DELETED') {
            this.registerCodeGenerationFileActivity(repositoryType, event.type, event.path, event.iteration);
            this.scheduleCodeGenerationRepositoryPull(repositoryType);
            return;
        }

        if (event.type === 'DONE') {
            const completionState = this.getCodeGenerationExecutionState(event);
            this.flushCodeGenerationRepositoryPull(repositoryType);
            this.refreshRequested.emit();
            this.updateCodeGenerationStatus(repositoryType, (status) => cloneWith(status, { state: completionState, attempts: event.attempts, message: event.message }));
            this.showCodeGenerationCompletionAlert(repositoryType, completionState);
            this.finishCurrentCodeGeneration(true);
            return;
        }

        if (event.type === 'ERROR') {
            this.stopCodeGenerationQueue(repositoryType, event.message, 'artemisApp.programmingExercise.codeGeneration.error');
        }
    }

    private finishCurrentCodeGeneration(continueQueue: boolean) {
        const hasMoreRepositories = continueQueue && this.queuedCodeGenerationRepositories.length > 0;
        this.clearJobSubscription(false);
        this.activeCodeGenerationRepository = undefined;

        if (hasMoreRepositories) {
            this.persistCodeGenerationState();
            this.waitForCodeGenerationSlotRelease();
        } else {
            this.isGeneratingCode.set(false);
            this.currentCodeGenerationUsesInitialIterationLimit = false;
            this.clearPersistedCodeGenerationState();
        }
    }

    private stopCodeGenerationQueue(
        repositoryType: SupportedCodeGenerationRepositoryType,
        message: string | undefined,
        alertTranslationKey: 'artemisApp.programmingExercise.codeGeneration.error' | 'artemisApp.programmingExercise.codeGeneration.timeout',
        showAlert = true,
    ) {
        this.updateCodeGenerationStatus(repositoryType, (status) => cloneWith(status, { state: 'error', message }));
        this.markQueuedCodeGenerationRepositoriesSkipped(repositoryType);
        this.queuedCodeGenerationRepositories = [];
        this.activeCodeGenerationRepository = undefined;
        this.currentCodeGenerationUsesInitialIterationLimit = false;
        this.clearCodeGenerationStatusSubscription();
        this.clearSlotReleasePoll();
        this.clearJobSubscription(true);
        this.clearPersistedCodeGenerationState();
        if (showAlert) {
            this.codeGenAlertService.addAlert({
                type: alertTranslationKey === 'artemisApp.programmingExercise.codeGeneration.timeout' ? AlertType.WARNING : AlertType.DANGER,
                translationKey: alertTranslationKey,
                translationParams: { repositoryType },
            });
        }
    }

    private markQueuedCodeGenerationRepositoriesSkipped(repositoryTypeToKeep?: SupportedCodeGenerationRepositoryType) {
        if (!this.queuedCodeGenerationRepositories.length) {
            return;
        }

        const skippedMessage = this.translateService.instant('artemisApp.programmingExercise.codeGeneration.status.skippedMessage');
        const queuedRepositories = new Set(this.queuedCodeGenerationRepositories.filter((repositoryType) => repositoryType !== repositoryTypeToKeep));
        if (!queuedRepositories.size) {
            return;
        }
        this.codeGenerationStatuses.update((statuses) =>
            statuses.map((status) => (queuedRepositories.has(status.repositoryType) ? cloneWith(status, { state: 'skipped', message: skippedMessage }) : status)),
        );
    }

    private registerCodeGenerationFileActivity(repositoryType: SupportedCodeGenerationRepositoryType, eventType: CodeGenerationFileEventType, path: string, iteration?: number) {
        const activity: CodeGenerationFileActivity = {
            repositoryType,
            eventType,
            path,
            iteration,
            timestamp: Date.now(),
        };
        this.updateCodeGenerationStatus(repositoryType, (status) => cloneWith(status, { fileActivities: [activity, ...status.fileActivities] }));
    }

    private getCodeGenerationExecutionState(event: Extract<HyperionEvent, { type: 'DONE' }>): Extract<CodeGenerationExecutionState, 'success' | 'warning' | 'error'> {
        const completionStatus: HyperionCompletionStatus = event.completionStatus ?? (event.success ? 'SUCCESS' : 'ERROR');
        switch (completionStatus) {
            case 'SUCCESS':
                return 'success';
            case 'PARTIAL':
                return 'warning';
            default:
                return 'error';
        }
    }

    private showCodeGenerationCompletionAlert(
        repositoryType: SupportedCodeGenerationRepositoryType,
        completionState: Extract<CodeGenerationExecutionState, 'success' | 'warning' | 'error'>,
    ) {
        const alertByState: Record<typeof completionState, { type: AlertType; translationKey: string }> = {
            success: {
                type: AlertType.SUCCESS,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
            },
            warning: {
                type: AlertType.WARNING,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.warning',
            },
            error: {
                type: AlertType.DANGER,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
            },
        };
        const alert = alertByState[completionState];
        this.codeGenAlertService.addAlert({
            type: alert.type,
            translationKey: alert.translationKey,
            translationParams: { repositoryType },
        });
    }

    private scheduleCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        if (this.selectedRepository() !== repositoryType) {
            return;
        }

        this.repositoriesWithPendingCodeGenerationPull.add(repositoryType);
        const existingTimeoutHandle = this.codeGenerationPullTimeoutHandles.get(repositoryType);
        if (existingTimeoutHandle) {
            clearTimeout(existingTimeoutHandle);
        }

        const timeoutHandle = window.setTimeout(() => {
            this.codeGenerationPullTimeoutHandles.delete(repositoryType);
            this.flushCodeGenerationRepositoryPull(repositoryType);
        }, CODE_GENERATION_FILE_PULL_DEBOUNCE_MS);
        this.codeGenerationPullTimeoutHandles.set(repositoryType, timeoutHandle);
    }

    private flushCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        this.clearScheduledCodeGenerationRepositoryPull(repositoryType);
        if (!this.canStartCodeGenerationRepositoryPull(repositoryType)) {
            return;
        }

        this.startCodeGenerationRepositoryPull(repositoryType);
    }

    private clearScheduledCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        const existingTimeoutHandle = this.codeGenerationPullTimeoutHandles.get(repositoryType);
        if (existingTimeoutHandle) {
            clearTimeout(existingTimeoutHandle);
            this.codeGenerationPullTimeoutHandles.delete(repositoryType);
        }
    }

    private canStartCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType): boolean {
        if (!this.repositoriesWithPendingCodeGenerationPull.has(repositoryType)) {
            return false;
        }

        if (this.selectedRepository() !== repositoryType) {
            this.repositoriesWithPendingCodeGenerationPull.delete(repositoryType);
            return false;
        }

        return !this.repositoriesWithInFlightCodeGenerationPull.has(repositoryType);
    }

    private startCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        this.repositoriesWithPendingCodeGenerationPull.delete(repositoryType);
        this.repositoriesWithInFlightCodeGenerationPull.add(repositoryType);
        this.codeGenerationPullSubscriptions.get(repositoryType)?.unsubscribe();
        const pullSubscription = this.repoService
            .pull()
            .pipe(
                take(1),
                catchError(() => {
                    return of(void 0);
                }),
            )
            .subscribe({
                complete: () => this.handleCompletedCodeGenerationRepositoryPull(repositoryType),
            });
        this.codeGenerationPullSubscriptions.set(repositoryType, pullSubscription);
    }

    private handleCompletedCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        this.codeGenerationPullSubscriptions.delete(repositoryType);
        this.repositoriesWithInFlightCodeGenerationPull.delete(repositoryType);
        if (this.repositoriesWithPendingCodeGenerationPull.has(repositoryType)) {
            this.flushCodeGenerationRepositoryPull(repositoryType);
        }
    }

    private clearCodeGenerationRepositoryPulls() {
        this.codeGenerationPullTimeoutHandles.forEach((timeoutHandle) => clearTimeout(timeoutHandle));
        this.codeGenerationPullTimeoutHandles.clear();
        this.codeGenerationPullSubscriptions.forEach((subscription) => subscription.unsubscribe());
        this.codeGenerationPullSubscriptions.clear();
        this.repositoriesWithPendingCodeGenerationPull.clear();
        this.repositoriesWithInFlightCodeGenerationPull.clear();
    }

    private getSelectedCodeGenerationRepositories(): SupportedCodeGenerationRepositoryType[] {
        const enabledRepositories = new Set(
            this.codeGenerationStatuses()
                .filter((status) => status.enabled)
                .map((status) => status.repositoryType),
        );

        return SUPPORTED_CODE_GENERATION_REPOSITORIES.filter((repositoryType) => enabledRepositories.has(repositoryType));
    }

    private initializeCodeGenerationRunStatuses(repositories: SupportedCodeGenerationRepositoryType[]) {
        const enabledRepositories = new Set(repositories);
        this.codeGenerationStatuses.set(
            SUPPORTED_CODE_GENERATION_REPOSITORIES.map((repositoryType) => ({
                repositoryType,
                enabled: enabledRepositories.has(repositoryType),
                state: enabledRepositories.has(repositoryType) ? 'queued' : 'idle',
                attempts: undefined,
                message: undefined,
                fileActivities: [],
            })),
        );
        this.persistCodeGenerationState();
    }

    private syncCodeGenerationSelectionWithSelectedRepository() {
        const repositoryType = this.mapRepositoryTypeToCodeGenerationRequest(this.selectedRepository());
        this.codeGenerationStatuses.update((statuses) => statuses.map((status) => cloneWith(status, { enabled: repositoryType === status.repositoryType })));
    }

    private createCodeGenerationStatus(repositoryType: SupportedCodeGenerationRepositoryType): CodeGenerationRepositoryStatus {
        return {
            repositoryType,
            enabled: false,
            state: 'idle',
            fileActivities: [],
        };
    }

    private updateCodeGenerationStatus(repositoryType: SupportedCodeGenerationRepositoryType, updater: (status: CodeGenerationRepositoryStatus) => CodeGenerationRepositoryStatus) {
        this.codeGenerationStatuses.update((statuses) => statuses.map((status) => (status.repositoryType === repositoryType ? updater(status) : status)));
        this.persistCodeGenerationState();
    }

    private restorePersistedCodeGenerationStatuses(repositoryType: SupportedCodeGenerationRepositoryType, persistedState?: PersistedCodeGenerationState) {
        if (persistedState) {
            this.codeGenerationStatuses.set(
                persistedState.statuses.map((status) => (status.repositoryType === repositoryType ? cloneWith(status, { enabled: true, state: 'running' }) : status)),
            );
            this.queuedCodeGenerationRepositories = [...persistedState.queuedRepositories];
            this.currentCodeGenerationUsesInitialIterationLimit = persistedState.initialAutoGeneration;
        } else {
            this.initializeCodeGenerationRunStatuses([repositoryType]);
            this.updateCodeGenerationStatus(repositoryType, (status) => cloneWith(status, { state: 'running' }));
            this.queuedCodeGenerationRepositories = [];
            this.currentCodeGenerationUsesInitialIterationLimit = false;
        }
        this.activeCodeGenerationRepository = repositoryType;
        this.isGeneratingCode.set(true);
        this.persistCodeGenerationState();
    }

    private restorePersistedCodeGenerationQueue(persistedState: PersistedCodeGenerationState) {
        this.codeGenerationStatuses.set(persistedState.statuses);
        this.queuedCodeGenerationRepositories = [...persistedState.queuedRepositories];
        this.activeCodeGenerationRepository = undefined;
        this.currentCodeGenerationUsesInitialIterationLimit = persistedState.initialAutoGeneration;
        this.isGeneratingCode.set(true);
        this.persistCodeGenerationState();
    }

    private persistCodeGenerationState() {
        const exerciseId = this.exercise().id;
        if (!exerciseId) {
            return;
        }
        this.sessionStorageService.store<PersistedCodeGenerationState>(this.getCodeGenerationStateStorageKey(exerciseId), {
            updatedAt: Date.now(),
            statuses: this.codeGenerationStatuses(),
            queuedRepositories: [...this.queuedCodeGenerationRepositories],
            activeRepository: this.activeCodeGenerationRepository,
            initialAutoGeneration: this.currentCodeGenerationUsesInitialIterationLimit,
        });
    }

    private loadPersistedCodeGenerationState(): PersistedCodeGenerationState | undefined {
        const exerciseId = this.exercise().id;
        if (!exerciseId) {
            return;
        }

        const key = this.getCodeGenerationStateStorageKey(exerciseId);
        try {
            const persistedState = this.sessionStorageService.retrieve<PersistedCodeGenerationState>(key);
            if (!persistedState || Date.now() - persistedState.updatedAt > CODE_GENERATION_STATUS_SESSION_STORAGE_TTL_MS) {
                this.sessionStorageService.remove(key);
                return;
            }

            const statuses = this.sanitizePersistedCodeGenerationStatuses(persistedState.statuses);
            if (!statuses.length) {
                this.sessionStorageService.remove(key);
                return;
            }

            const activeRepository = this.sanitizePersistedCodeGenerationRepositoryType(persistedState.activeRepository);
            const queuedRepositories = this.sanitizePersistedCodeGenerationQueuedRepositories(persistedState.queuedRepositories, activeRepository);

            return cloneWith(persistedState, { statuses, queuedRepositories, activeRepository, initialAutoGeneration: !!persistedState.initialAutoGeneration });
        } catch {
            this.sessionStorageService.remove(key);
            return;
        }
    }

    private sanitizePersistedCodeGenerationStatuses(statuses: CodeGenerationRepositoryStatus[] | undefined): CodeGenerationRepositoryStatus[] {
        if (!Array.isArray(statuses)) {
            return [];
        }

        const validStatuses = statuses
            .filter((status): status is CodeGenerationRepositoryStatus => !!status && this.isSupportedCodeGenerationRepositoryType(status.repositoryType))
            .map((status) => ({
                repositoryType: status.repositoryType,
                enabled: !!status.enabled,
                state: this.isCodeGenerationExecutionState(status.state) ? status.state : 'idle',
                attempts: typeof status.attempts === 'number' ? status.attempts : undefined,
                message: typeof status.message === 'string' ? status.message : undefined,
                fileActivities: this.sanitizePersistedCodeGenerationFileActivities(status.fileActivities, status.repositoryType),
            }));
        if (!validStatuses.length) {
            return [];
        }

        const persistedStatusesByRepository = new Map(validStatuses.map((status) => [status.repositoryType, status]));
        return SUPPORTED_CODE_GENERATION_REPOSITORIES.map((repositoryType) => persistedStatusesByRepository.get(repositoryType) ?? this.createCodeGenerationStatus(repositoryType));
    }

    private sanitizePersistedCodeGenerationFileActivities(
        fileActivities: CodeGenerationFileActivity[] | undefined,
        repositoryType: SupportedCodeGenerationRepositoryType,
    ): CodeGenerationFileActivity[] {
        if (!Array.isArray(fileActivities)) {
            return [];
        }

        return fileActivities
            .filter((activity): activity is CodeGenerationFileActivity => !!activity && this.isCodeGenerationFileEventType(activity.eventType))
            .map((activity) => ({
                repositoryType,
                eventType: activity.eventType,
                path: typeof activity.path === 'string' ? activity.path : '',
                iteration: typeof activity.iteration === 'number' ? activity.iteration : undefined,
                timestamp: typeof activity.timestamp === 'number' ? activity.timestamp : Date.now(),
            }))
            .filter((activity) => !!activity.path);
    }

    private sanitizePersistedCodeGenerationQueuedRepositories(
        queuedRepositories: SupportedCodeGenerationRepositoryType[] | undefined,
        activeRepository?: SupportedCodeGenerationRepositoryType,
    ): SupportedCodeGenerationRepositoryType[] {
        if (!Array.isArray(queuedRepositories)) {
            return [];
        }

        const seenRepositories = new Set<SupportedCodeGenerationRepositoryType>();
        return queuedRepositories.filter((repositoryType): repositoryType is SupportedCodeGenerationRepositoryType => {
            if (!this.isSupportedCodeGenerationRepositoryType(repositoryType) || repositoryType === activeRepository || seenRepositories.has(repositoryType)) {
                return false;
            }

            seenRepositories.add(repositoryType);
            return true;
        });
    }

    private sanitizePersistedCodeGenerationRepositoryType(repositoryType: unknown): SupportedCodeGenerationRepositoryType | undefined {
        return this.isSupportedCodeGenerationRepositoryType(repositoryType) ? repositoryType : undefined;
    }

    private clearPersistedCodeGenerationState() {
        const exerciseId = this.exercise().id;
        if (!exerciseId) {
            return;
        }
        this.sessionStorageService.remove(this.getCodeGenerationStateStorageKey(exerciseId));
    }

    private getCodeGenerationStateStorageKey(exerciseId: number): string {
        return `${CODE_GENERATION_STATUS_SESSION_STORAGE_KEY_PREFIX}${exerciseId}`;
    }

    private isSupportedCodeGenerationRepositoryType(repositoryType: unknown): repositoryType is SupportedCodeGenerationRepositoryType {
        return SUPPORTED_CODE_GENERATION_REPOSITORIES.includes(repositoryType as SupportedCodeGenerationRepositoryType);
    }

    private isCodeGenerationExecutionState(state: unknown): state is CodeGenerationExecutionState {
        return ['idle', 'queued', 'running', 'success', 'warning', 'error', 'skipped', 'unknown'].includes(state as CodeGenerationExecutionState);
    }

    private isCodeGenerationFileEventType(eventType: unknown): eventType is CodeGenerationFileEventType {
        return eventType === 'FILE_UPDATED' || eventType === 'NEW_FILE' || eventType === 'FILE_DELETED';
    }

    private waitForCodeGenerationSlotRelease(attempt = 1) {
        this.clearSlotReleasePoll();
        const exerciseId = this.exercise().id;
        if (!exerciseId) {
            this.clearJobSubscription(true);
            return;
        }

        this.clearCodeGenerationStatusSubscription();
        this.statusSubscription = this.hyperionCodeGenerationApi.getActiveCodeGenerationJob(exerciseId).subscribe({
            next: (res) => {
                if (!res?.jobId) {
                    this.clearCodeGenerationStatusSubscription();
                    this.clearSlotReleasePoll();
                    this.runNextCodeGeneration();
                    return;
                }

                this.scheduleNextSlotReleasePoll(attempt);
            },
            error: () => {
                this.scheduleNextSlotReleasePoll(attempt);
            },
        });
    }

    private scheduleNextSlotReleasePoll(attempt: number) {
        if (attempt >= CODE_GENERATION_SLOT_RELEASE_MAX_POLLS) {
            const nextRepository = this.queuedCodeGenerationRepositories[0];
            if (nextRepository) {
                this.stopCodeGenerationQueue(
                    nextRepository,
                    this.translateService.instant('artemisApp.programmingExercise.codeGeneration.queueReleaseTimeoutDetails'),
                    'artemisApp.programmingExercise.codeGeneration.error',
                );
            } else {
                this.clearJobSubscription(true);
            }
            return;
        }

        this.clearSlotReleasePoll();
        this.slotReleasePollTimeoutHandle = window.setTimeout(() => {
            this.waitForCodeGenerationSlotRelease(attempt + 1);
        }, CODE_GENERATION_SLOT_RELEASE_POLL_INTERVAL_MS);
    }

    private clearCodeGenerationStatusSubscription() {
        this.statusSubscription?.unsubscribe();
        this.statusSubscription = undefined;
    }

    private clearSlotReleasePoll() {
        if (this.slotReleasePollTimeoutHandle) {
            clearTimeout(this.slotReleasePollTimeoutHandle);
            this.slotReleasePollTimeoutHandle = undefined;
        }
    }
}
