import { EMPTY, Observable, map, take } from 'rxjs';
import { vi } from 'vitest';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionTarget } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';

/**
 * Stands in for the EntityDeletionService in component specs: the delete request is sent as usual and the deletion ends right away with
 * {@link status}, without a websocket or a progress dialog.
 */
export class MockEntityDeletionService {
    /** How the next deletions end. */
    status = EntityDeletionStatus.COMPLETED;
    errorMessage?: string;

    delete = vi.fn((target: EntityDeletionTarget, request$: Observable<unknown>): Observable<EntityDeletionProgress> => {
        return request$.pipe(
            take(1),
            map(() => this.finalProgress(target)),
        );
    });

    /** Finds no running deletion. The lifetime the caller passed is recorded in the calls. */
    resume = vi.fn<(target: EntityDeletionTarget, lifetime: unknown) => Observable<EntityDeletionProgress>>(() => EMPTY);

    private finalProgress(target: EntityDeletionTarget): EntityDeletionProgress {
        return {
            entityType: target.entityType,
            entityId: target.entityId,
            status: this.status,
            stepsCompleted: 1,
            totalSteps: 1,
            itemsProcessed: 0,
            totalItems: 0,
            weightedProgressPercent: this.status === EntityDeletionStatus.COMPLETED ? 100 : 40,
            errorMessage: this.errorMessage,
        };
    }
}
