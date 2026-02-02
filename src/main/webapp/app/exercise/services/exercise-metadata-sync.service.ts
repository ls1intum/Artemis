import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, firstValueFrom } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
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
import { ExerciseMetadataConflictModalComponent, ExerciseMetadataConflictModalResult } from 'app/exercise/synchronization/exercise-metadata-conflict-modal.component';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';
import { convertDateFromServer } from 'app/shared/util/date.utils';

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

@Injectable({ providedIn: 'root' })
export class ExerciseMetadataSyncService {
    private readonly exerciseEditorSyncService = inject(ExerciseEditorSyncService);
    private readonly http = inject(HttpClient);
    private readonly modalService = inject(NgbModal);

    private context?: ExerciseMetadataSyncContext<Exercise>;
    private subscriptionActive = false;
    private updateSubscription?: Subscription;
    private readonly pendingAlerts = signal<ExerciseNewVersionAlertEvent[]>([]);
    private readonly processing = signal<boolean>(false);

    initialize<T extends Exercise>(context: ExerciseMetadataSyncContext<T>): void {
        this.context = context as ExerciseMetadataSyncContext<Exercise>;
        if (this.subscriptionActive) {
            return;
        }
        this.subscriptionActive = true;
        this.updateSubscription = this.exerciseEditorSyncService.subscribeToUpdates(context.exerciseId).subscribe((event: ExerciseEditorSyncEvent) => this.handleEvent(event));
    }

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

    private handleEvent(event: ExerciseEditorSyncEvent): void {
        if (event.eventType !== ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT || event.target !== ExerciseEditorSyncTarget.EXERCISE_METADATA) {
            return;
        }
        this.enqueueAlert(event);
    }

    private enqueueAlert(event: ExerciseNewVersionAlertEvent): void {
        const queued = this.pendingAlerts();
        const updated = queued.slice();
        updated.push(event);
        this.pendingAlerts.set(updated);
        this.processQueue();
    }

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

    private async processAlert(alert: ExerciseNewVersionAlertEvent): Promise<void> {
        const context = this.context;
        if (!context) {
            return;
        }

        const snapshot = await this.fetchSnapshot(context.exerciseId, alert.exerciseVersionId);
        if (!snapshot) {
            return;
        }

        const handlers = this.getHandlers(context.exerciseType);
        const changedFields = alert.changedFields ?? [];
        const applicableHandlers = handlers.filter((handler) => changedFields.includes(handler.key));

        if (applicableHandlers.length === 0) {
            this.applySnapshotToBaseline(context, snapshot, handlers, changedFields);
            return;
        }

        const conflicts = this.collectConflicts(context, snapshot, applicableHandlers);
        this.applyNonConflictingChanges(context, snapshot, applicableHandlers, conflicts);
        this.applySnapshotToBaseline(context, snapshot, handlers, changedFields);

        if (conflicts.length === 0) {
            return;
        }

        const resolution = await this.openConflictModal(conflicts, alert.author, alert.exerciseVersionId);
        if (!resolution) {
            return;
        }
        this.applyConflictResolution(context, snapshot, resolution);
    }

    private async fetchSnapshot(exerciseId: number, versionId: number): Promise<ExerciseSnapshotDTO | undefined> {
        try {
            return await firstValueFrom(this.http.get<ExerciseSnapshotDTO>(`api/exercise/${exerciseId}/version/${versionId}`));
        } catch {
            return undefined;
        }
    }

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

    private async openConflictModal(conflicts: ConflictCandidate[], author: UserPublicInfoDTO, versionId: number): Promise<ExerciseMetadataConflictModalResult | undefined> {
        const modalRef = this.modalService.open(ExerciseMetadataConflictModalComponent, { size: 'lg', backdrop: 'static', keyboard: false });
        modalRef.componentInstance.setConflicts(conflicts);
        modalRef.componentInstance.setAuthor(author);
        modalRef.componentInstance.setVersionId(versionId);

        try {
            return (await modalRef.result) as ExerciseMetadataConflictModalResult;
        } catch {
            return undefined;
        }
    }

    private applyConflictResolution<T extends Exercise>(
        context: ExerciseMetadataSyncContext<T>,
        snapshot: ExerciseSnapshotDTO,
        resolution: ExerciseMetadataConflictModalResult,
    ): void {
        const currentExercise = context.getCurrentExercise();
        const handlers = this.getHandlers(context.exerciseType);
        const handlerMap = new Map(handlers.map((handler) => [handler.key, handler]));

        for (const decision of resolution.decisions) {
            if (!decision.useIncoming) {
                continue;
            }
            const handler = handlerMap.get(decision.field);
            if (!handler) {
                continue;
            }
            const incomingValue = handler.getIncomingValue(snapshot);
            handler.applyValue(currentExercise, incomingValue);
        }
    }

    private valuesEqual(value: unknown, otherValue: unknown): boolean {
        if (dayjs.isDayjs(value) || dayjs.isDayjs(otherValue)) {
            const normalizedLeft = dayjs.isDayjs(value) ? value : convertDateFromServer(value as any);
            const normalizedRight = dayjs.isDayjs(otherValue) ? otherValue : convertDateFromServer(otherValue as any);
            if (!normalizedLeft || !normalizedRight) {
                return false;
            }
            return normalizedLeft.isSame(normalizedRight);
        }
        return isEqual(value, otherValue);
    }

    private getHandlers(exerciseType: ExerciseType): ExerciseMetadataFieldHandler<Exercise>[] {
        return createExerciseMetadataHandlers(exerciseType);
    }
}
