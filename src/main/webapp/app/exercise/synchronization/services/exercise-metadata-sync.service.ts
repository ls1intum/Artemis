import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, firstValueFrom } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import dayjs from 'dayjs/esm';
import isEqual from 'lodash-es/isEqual';

import { AlertService } from 'app/shared/service/alert.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ExerciseNewVersionAlertEvent,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { ExerciseMetadataFieldHandler, createExerciseMetadataHandlers } from 'app/exercise/synchronization/metadata/exercise-metadata-handlers';
import {
    ExerciseMetadataConflictModalComponent,
    ExerciseMetadataConflictModalData,
    ExerciseMetadataConflictModalResult,
} from 'app/exercise/synchronization/metadata/exercise-metadata-conflict-modal.component';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';

/**
 * Context required to apply metadata synchronization to a live exercise editor.
 */
export interface ExerciseMetadataSyncContext<T extends Exercise> {
    exerciseId: number;
    exerciseType: ExerciseType;
    getCurrentExercise: () => T;
    getBaselineExercise: () => T;
    setBaselineExercise: (exercise: T) => void;
}

interface ConflictCandidate {
    field: string;
    labelKey: string;
    currentValue: unknown;
    incomingValue: unknown;
}

/**
 * Fields excluded from metadata sync because they are handled by a separate synchronization mechanism (e.g. Yjs live sync).
 */
const METADATA_SYNC_EXCLUDED_FIELDS = new Set(['problemStatement']);

/**
 * Type guard for competency link arrays. This relies on the presence of the `competency`
 * property, which is specific to `CompetencyExerciseLink` objects in the exercise domain.
 * It is only called within `metadataValuesEqual` where the values originate from exercise
 * field handlers, so false positives from unrelated object shapes are not a concern.
 */
const isCompetencyLinkArray = (value: unknown): value is Array<{ competency?: { id?: number }; weight?: number }> => {
    if (!Array.isArray(value) || value.length === 0) {
        return false;
    }
    return value.every((entry) => entry && typeof entry === 'object' && 'competency' in entry);
};

const normalizeCompetencyLinks = (value: unknown): Array<{ competencyId?: number; weight?: number }> | undefined => {
    if (!Array.isArray(value)) {
        return undefined;
    }
    const normalized = value
        .filter((entry) => entry && typeof entry === 'object')
        .map((entry) => {
            const link = entry as { competency?: { id?: number }; weight?: number };
            return {
                competencyId: link.competency?.id,
                weight: link.weight,
            };
        });
    return normalized.sort((left, right) => {
        const leftId = left.competencyId ?? -1;
        const rightId = right.competencyId ?? -1;
        if (leftId !== rightId) {
            return leftId - rightId;
        }
        const leftWeight = left.weight ?? -1;
        const rightWeight = right.weight ?? -1;
        return leftWeight - rightWeight;
    });
};

/**
 * Compares values including dayjs timestamps with normalized server dates.
 * Exported for direct testing.
 */
export const metadataValuesEqual = (value: unknown, otherValue: unknown): boolean => {
    // Treat null and undefined as equivalent absent values
    const isAbsent = (v: unknown): boolean => v === undefined || v === null;
    if (isAbsent(value) && isAbsent(otherValue)) {
        return true;
    }
    if (isCompetencyLinkArray(value) || isCompetencyLinkArray(otherValue)) {
        return isEqual(normalizeCompetencyLinks(value), normalizeCompetencyLinks(otherValue));
    }
    if (dayjs.isDayjs(value) || dayjs.isDayjs(otherValue)) {
        const normalizedLeft = dayjs.isDayjs(value) ? value : typeof value === 'string' ? dayjs(value) : undefined;
        const normalizedRight = dayjs.isDayjs(otherValue) ? otherValue : typeof otherValue === 'string' ? dayjs(otherValue) : undefined;
        const leftValid = normalizedLeft?.isValid() ?? false;
        const rightValid = normalizedRight?.isValid() ?? false;
        if (!leftValid || !rightValid) {
            // Both absent/invalid → equal; one valid and one not → not equal
            return leftValid === rightValid;
        }
        return normalizedLeft!.isSame(normalizedRight!);
    }
    return isEqual(value, otherValue);
};

/**
 * Synchronizes exercise metadata changes from other editors into the current
 * exercise editor session.
 *
 * This service is intentionally `providedIn: 'root'` (singleton) because it
 * shares the WebSocket subscription managed by {@link ExerciseEditorSyncService},
 * which is also root-scoped. Only one exercise can be edited at a time, so the
 * singleton holds mutable state for the active exercise. Calling {@link initialize}
 * with a different exercise ID automatically tears down the previous state.
 *
 * Consumers MUST call {@link destroy} in their `ngOnDestroy` to clean up
 * the WebSocket subscription and reset internal state before navigating away.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseMetadataSyncService {
    private readonly exerciseEditorSyncService = inject(ExerciseEditorSyncService);
    private readonly http = inject(HttpClient);
    private readonly dialogService = inject(DialogService);
    private readonly alertService = inject(AlertService);

    private context?: ExerciseMetadataSyncContext<Exercise>;
    private cachedHandlers?: ExerciseMetadataFieldHandler<Exercise>[];
    private subscriptionActive = false;
    private updateSubscription?: Subscription;
    private readonly pendingAlerts = signal<ExerciseNewVersionAlertEvent[]>([]);
    private readonly processing = signal<boolean>(false);

    /**
     * Initializes metadata synchronization for an exercise update context.
     * Subsequent calls are ignored while a subscription is active.
     *
     * @param context the metadata synchronization context
     */
    initialize<T extends Exercise>(context: ExerciseMetadataSyncContext<T>): void {
        if (this.subscriptionActive && this.context?.exerciseId !== context.exerciseId) {
            this.destroy();
        }
        this.context = context as ExerciseMetadataSyncContext<Exercise>;
        this.cachedHandlers = this.buildHandlers(this.context);
        if (this.subscriptionActive) {
            return;
        }
        this.subscriptionActive = true;
        this.updateSubscription = this.exerciseEditorSyncService.subscribeToUpdates(context.exerciseId).subscribe((event: ExerciseEditorSyncEvent) => this.handleEvent(event));
    }

    /**
     * Cleans up the websocket subscription and resets internal state.
     *
     * Note: This unconditionally calls `exerciseEditorSyncService.unsubscribe()`
     * which tears down the shared WebSocket subscription. This is safe because all
     * services consuming the shared subscription (ExerciseMetadataSyncService,
     * ProblemStatementSyncService) live in the same exercise editor component and
     * are always destroyed together. Calling `unsubscribe()` without a prior
     * `initialize()` is harmless — the shared service treats redundant unsubscribe
     * calls as no-ops.
     */
    destroy(): void {
        if (this.updateSubscription) {
            this.updateSubscription.unsubscribe();
            this.updateSubscription = undefined;
        }
        this.subscriptionActive = false;
        this.pendingAlerts.set([]);
        this.processing.set(false);
        this.context = undefined;
        this.cachedHandlers = undefined;
        this.exerciseEditorSyncService.unsubscribe();
    }

    /**
     * Filters incoming events and enqueues relevant metadata alerts.
     *
     * @param event the incoming synchronization event
     */
    private handleEvent(event: ExerciseEditorSyncEvent): void {
        if (event.eventType !== ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT || event.target !== ExerciseEditorSyncTarget.EXERCISE_METADATA) {
            return;
        }
        this.enqueueAlert(event);
    }

    /**
     * Adds an alert to the processing queue.
     *
     * @param event the new version alert event
     */
    private enqueueAlert(event: ExerciseNewVersionAlertEvent): void {
        const queued = this.pendingAlerts();
        const updated = queued.slice();
        updated.push(event);
        this.pendingAlerts.set(updated);
        this.processQueue();
    }

    /**
     * Processes the next queued alert if none is currently in progress.
     */
    private processQueue(): void {
        if (this.processing() || !this.context) {
            return;
        }
        const queued = this.pendingAlerts();
        const nextAlert = queued[0];
        if (!nextAlert) {
            return;
        }
        this.processing.set(true);
        void this.processAlert(nextAlert).finally(() => {
            const remaining = this.pendingAlerts().slice(1);
            this.pendingAlerts.set(remaining);
            this.processing.set(false);
            if (remaining.length > 0) {
                this.processQueue();
            }
        });
    }

    /**
     * Processes a single metadata alert by fetching the snapshot and applying updates.
     *
     * @param alert the alert to process
     */
    private async processAlert(alert: ExerciseNewVersionAlertEvent): Promise<void> {
        const context = this.context;
        if (!context) {
            return;
        }

        const handlers = this.getHandlers();
        const changedFields = alert.changedFields ?? [];
        const effectiveFields = changedFields.filter((field) => !METADATA_SYNC_EXCLUDED_FIELDS.has(field));
        if (effectiveFields.length === 0) {
            // No actionable fields — either changedFields was empty/undefined or all fields
            // are handled by a separate sync mechanism (e.g. Yjs for problemStatement).
            return;
        }

        const snapshot = await this.fetchSnapshot(context.exerciseId, alert.exerciseVersionId);
        if (!snapshot) {
            return;
        }

        const applicableHandlers = handlers.filter((handler) => effectiveFields.includes(handler.key));

        if (applicableHandlers.length === 0) {
            this.applySnapshotToBaseline(context, snapshot, handlers, effectiveFields);
            return;
        }

        const conflicts = this.collectConflicts(context, snapshot, applicableHandlers);
        this.applyNonConflictingChanges(context, snapshot, applicableHandlers, conflicts);
        this.applySnapshotToBaseline(context, snapshot, handlers, effectiveFields);

        if (conflicts.length === 0) {
            return;
        }

        const resolution = await this.openConflictModal(conflicts, alert.author, alert.exerciseVersionId);
        if (!resolution || !this.context) {
            return;
        }
        this.applyConflictResolution(context, snapshot, resolution, handlers);
    }

    /**
     * Fetches an exercise snapshot for a given version.
     *
     * @param exerciseId the exercise id
     * @param versionId the version id
     * @returns the snapshot or undefined if the request fails
     */
    private async fetchSnapshot(exerciseId: number, versionId: number): Promise<ExerciseSnapshotDTO | undefined> {
        try {
            return await firstValueFrom(this.http.get<ExerciseSnapshotDTO>(`api/exercise/${exerciseId}/version/${versionId}`));
        } catch {
            this.alertService.warning('artemisApp.exercise.metadataSync.snapshotFetchFailed');
            return undefined;
        }
    }

    /**
     * Collects conflicts by comparing current, baseline, and incoming values.
     *
     * @param context the metadata synchronization context
     * @param snapshot the incoming exercise snapshot
     * @param handlers handlers used to resolve metadata fields
     * @returns a list of conflicting fields
     */
    private collectConflicts<T extends Exercise>(
        context: ExerciseMetadataSyncContext<T>,
        snapshot: ExerciseSnapshotDTO,
        handlers: ExerciseMetadataFieldHandler<T>[],
    ): ConflictCandidate[] {
        const currentExercise = context.getCurrentExercise();
        const baselineExercise = context.getBaselineExercise();
        const conflicts: ConflictCandidate[] = [];

        for (const handler of handlers) {
            const currentValue = handler.getCurrentValue(currentExercise);
            const baselineValue = handler.getBaselineValue(baselineExercise);
            const incomingValue = handler.getIncomingValue(snapshot);

            const hasLocalChange = !metadataValuesEqual(currentValue, baselineValue);
            const hasIncomingChange = !metadataValuesEqual(incomingValue, baselineValue);

            if (hasLocalChange && hasIncomingChange && !metadataValuesEqual(currentValue, incomingValue)) {
                conflicts.push({
                    field: handler.key,
                    labelKey: handler.labelKey,
                    currentValue,
                    incomingValue,
                });
            }
        }

        return conflicts;
    }

    /**
     * Applies incoming changes for fields without conflicts.
     *
     * @param context the metadata synchronization context
     * @param snapshot the incoming exercise snapshot
     * @param handlers handlers used to resolve metadata fields
     * @param conflicts list of conflict candidates
     */
    private applyNonConflictingChanges<T extends Exercise>(
        context: ExerciseMetadataSyncContext<T>,
        snapshot: ExerciseSnapshotDTO,
        handlers: ExerciseMetadataFieldHandler<T>[],
        conflicts: ConflictCandidate[],
    ): void {
        const currentExercise = context.getCurrentExercise();
        const conflictFields = new Set(conflicts.map((conflict) => conflict.field));

        for (const handler of handlers) {
            if (conflictFields.has(handler.key)) {
                continue;
            }
            const incomingValue = handler.getIncomingValue(snapshot);
            handler.applyValue(currentExercise, incomingValue);
        }
    }

    /**
     * Updates the baseline snapshot for changed fields.
     *
     * This method mutates the baseline exercise object **in place** and then
     * calls {@link ExerciseMetadataSyncContext.setBaselineExercise} with the
     * same reference. The setter call is intentional: it notifies the consumer
     * that the baseline was updated so it can trigger side effects (e.g.
     * re-assignment for Angular binding), even though the object identity
     * has not changed.
     *
     * @param context the metadata synchronization context
     * @param snapshot the incoming exercise snapshot
     * @param handlers handlers used to resolve metadata fields
     * @param changedFields list of changed field identifiers
     */
    private applySnapshotToBaseline<T extends Exercise>(
        context: ExerciseMetadataSyncContext<T>,
        snapshot: ExerciseSnapshotDTO,
        handlers: ExerciseMetadataFieldHandler<T>[],
        changedFields: string[],
    ): void {
        const baselineExercise = context.getBaselineExercise();
        const fieldSet = new Set(changedFields);
        for (const handler of handlers) {
            if (!fieldSet.has(handler.key)) {
                continue;
            }
            const incomingValue = handler.getIncomingValue(snapshot);
            handler.applyValue(baselineExercise, incomingValue);
        }
        context.setBaselineExercise(baselineExercise);
    }

    /**
     * Opens the conflict resolution modal for conflicting fields.
     *
     * @param conflicts the list of conflicts to display
     * @param author the author of the incoming version
     * @param versionId the incoming version id
     * @returns the modal result or undefined if dismissed
     */
    private async openConflictModal(conflicts: ConflictCandidate[], author: UserPublicInfoDTO, versionId: number): Promise<ExerciseMetadataConflictModalResult | undefined> {
        const data: ExerciseMetadataConflictModalData = {
            conflicts,
            author,
            versionId,
            exerciseId: this.context?.exerciseId,
            exerciseType: this.context?.exerciseType,
        };

        const dialogRef = this.dialogService.open(ExerciseMetadataConflictModalComponent, {
            width: '80rem',
            modal: true,
            closable: false,
            closeOnEscape: false,
            dismissableMask: false,
            data,
        });

        if (!dialogRef) {
            return undefined;
        }
        try {
            return (await firstValueFrom(dialogRef.onClose)) as ExerciseMetadataConflictModalResult | undefined;
        } catch {
            // firstValueFrom rejects if the Observable completes without emitting (e.g. dialog destroyed externally)
            return undefined;
        }
    }

    /**
     * Applies conflict resolution decisions to the current exercise.
     *
     * @param context the metadata synchronization context
     * @param snapshot the incoming exercise snapshot
     * @param resolution the modal resolution with per-field decisions
     * @param handlers the same handler instances used during conflict detection
     */
    private applyConflictResolution(
        context: ExerciseMetadataSyncContext<Exercise>,
        snapshot: ExerciseSnapshotDTO,
        resolution: ExerciseMetadataConflictModalResult,
        handlers: ExerciseMetadataFieldHandler<Exercise>[],
    ): void {
        const currentExercise = context.getCurrentExercise();
        const handlerMap = new Map(handlers.map((handler) => [handler.key, handler]));

        for (const decision of resolution.decisions) {
            const handler = handlerMap.get(decision.field);
            if (!handler) {
                continue;
            }
            if (!decision.useIncoming) {
                continue;
            }
            const incomingValue = handler.getIncomingValue(snapshot);
            handler.applyValue(currentExercise, incomingValue);
        }
    }

    /**
     * Returns the cached metadata handlers for the current context.
     * Handlers are built once in {@link initialize} and reused across all alerts.
     */
    private getHandlers(): ExerciseMetadataFieldHandler<Exercise>[] {
        return this.cachedHandlers ?? [];
    }

    /**
     * Builds metadata handlers for a given context.
     */
    private buildHandlers(context: ExerciseMetadataSyncContext<Exercise>): ExerciseMetadataFieldHandler<Exercise>[] {
        return createExerciseMetadataHandlers(context.exerciseType, () => context.getCurrentExercise());
    }
}
