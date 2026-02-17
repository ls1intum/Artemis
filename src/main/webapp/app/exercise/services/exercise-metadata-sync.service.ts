import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, firstValueFrom } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';
import dayjs from 'dayjs/esm';
import isEqual from 'lodash-es/isEqual';

import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ExerciseNewVersionAlertEvent,
} from 'app/exercise/services/exercise-editor-sync.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { ExerciseMetadataFieldHandler, createExerciseMetadataHandlers } from 'app/exercise/synchronization/exercise-metadata-handlers';
import {
    ExerciseMetadataConflictModalComponent,
    ExerciseMetadataConflictModalData,
    ExerciseMetadataConflictModalResult,
} from 'app/exercise/synchronization/exercise-metadata-conflict-modal.component';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

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

@Injectable({ providedIn: 'root' })
export class ExerciseMetadataSyncService {
    private readonly exerciseEditorSyncService = inject(ExerciseEditorSyncService);
    private readonly http = inject(HttpClient);
    private readonly dialogService = inject(DialogService);

    private context?: ExerciseMetadataSyncContext<Exercise>;
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
        if (this.subscriptionActive) {
            return;
        }
        this.subscriptionActive = true;
        this.updateSubscription = this.exerciseEditorSyncService.subscribeToUpdates(context.exerciseId).subscribe((event: ExerciseEditorSyncEvent) => this.handleEvent(event));
    }

    /**
     * Cleans up the websocket subscription and resets internal state.
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

        const handlers = this.getHandlers(context);
        const changedFields = alert.changedFields ?? [];
        const effectiveFields = changedFields.filter((field) => !METADATA_SYNC_EXCLUDED_FIELDS.has(field));
        if (effectiveFields.length === 0) {
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
        if (!resolution) {
            return;
        }
        this.applyConflictResolution(context, snapshot, resolution);
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

            const hasLocalChange = !this.valuesEqual(currentValue, baselineValue);
            const hasIncomingChange = !this.valuesEqual(incomingValue, baselineValue);

            if (hasLocalChange && hasIncomingChange && !this.valuesEqual(currentValue, incomingValue)) {
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

        return firstValueFrom(dialogRef!.onClose) as Promise<ExerciseMetadataConflictModalResult | undefined>;
    }

    /**
     * Applies conflict resolution decisions to the current exercise.
     *
     * @param context the metadata synchronization context
     * @param snapshot the incoming exercise snapshot
     * @param resolution the modal resolution with per-field decisions
     */
    private applyConflictResolution<T extends Exercise>(
        context: ExerciseMetadataSyncContext<T>,
        snapshot: ExerciseSnapshotDTO,
        resolution: ExerciseMetadataConflictModalResult,
    ): void {
        const currentExercise = context.getCurrentExercise();
        const handlers = this.getHandlers(context);
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
     * Compares values including dayjs timestamps with normalized server dates.
     *
     * @param value the left value
     * @param otherValue the right value
     * @returns true if the values are considered equal
     */
    private valuesEqual(value: unknown, otherValue: unknown): boolean {
        if (value == undefined && otherValue == undefined) {
            return true;
        }
        if (this.isCompetencyLinkArray(value) || this.isCompetencyLinkArray(otherValue)) {
            return isEqual(this.normalizeCompetencyLinks(value), this.normalizeCompetencyLinks(otherValue));
        }
        if (dayjs.isDayjs(value) || dayjs.isDayjs(otherValue)) {
            const normalizedLeft = dayjs.isDayjs(value) ? value : typeof value === 'string' ? dayjs(value) : undefined;
            const normalizedRight = dayjs.isDayjs(otherValue) ? otherValue : typeof otherValue === 'string' ? dayjs(otherValue) : undefined;
            if (!normalizedLeft?.isValid() || !normalizedRight?.isValid()) {
                return normalizedLeft === normalizedRight;
            }
            return normalizedLeft.isSame(normalizedRight);
        }
        return isEqual(value, otherValue);
    }

    private isCompetencyLinkArray(value: unknown): value is Array<{ competency?: { id?: number }; weight?: number }> {
        if (!Array.isArray(value)) {
            return false;
        }
        return value.some((entry) => {
            if (!entry || typeof entry !== 'object') {
                return false;
            }
            return 'competency' in entry || 'weight' in entry;
        });
    }

    private normalizeCompetencyLinks(value: unknown): Array<{ competencyId?: number; weight?: number }> | undefined {
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
    }

    /**
     * Creates the metadata handlers for a given exercise type.
     *
     * @param context the metadata synchronization context
     * @returns the handlers used for metadata resolution
     */
    private getHandlers(context: ExerciseMetadataSyncContext<Exercise>): ExerciseMetadataFieldHandler<Exercise>[] {
        return createExerciseMetadataHandlers(context.exerciseType, () => context.getCurrentExercise());
    }
}
