import { ComponentFixture, TestBed } from '@angular/core/testing';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { OutboxQueue, OutboxQueueEntry } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';
import { CourseIngestionQueueComponent } from './course-ingestion-queue.component';

describe('CourseIngestionQueueComponent', () => {
    let fixture: ComponentFixture<CourseIngestionQueueComponent>;
    let component: CourseIngestionQueueComponent;
    let getOutboxQueue: Mock<() => ReturnType<CourseIngestionDashboardService['getOutboxQueue']>>;
    let getReconcileStatus: Mock<() => ReturnType<CourseIngestionDashboardService['getReconcileStatus']>>;

    const entry = (id: number, overrides: Partial<OutboxQueueEntry> = {}): OutboxQueueEntry => ({
        id,
        operation: 'UPSERT',
        entityType: 'lecture',
        entityId: 100 + id,
        origin: 'RECONCILE_MISSING',
        attempts: 0,
        ...overrides,
    });

    const queue = (entries: OutboxQueueEntry[], totalDepth = entries.length): OutboxQueue => ({ totalDepth, entries });

    async function createComponent() {
        fixture = TestBed.createComponent(CourseIngestionQueueComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    beforeEach(async () => {
        vi.useFakeTimers();
        getOutboxQueue = vi.fn().mockReturnValue(of(queue([entry(1), entry(2)])));
        getReconcileStatus = vi.fn().mockReturnValue(of({ passes: [], ledger: [] }));
        await TestBed.configureTestingModule({
            imports: [CourseIngestionQueueComponent],
            providers: [provideTranslateService(), { provide: CourseIngestionDashboardService, useValue: { getOutboxQueue, getReconcileStatus } }],
        }).compileComponents();
        await createComponent();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    it('lists queued rows in dispatch order', () => {
        expect(component['rows']().map((row) => row.id)).toEqual([1, 2]);
        expect(component['rows']().every((row) => row.status === 'queued')).toBe(true);
        expect(component['totalDepth']()).toBe(2);
    });

    it('marks a row that is backing off after a failure as retrying', () => {
        const later = new Date(Date.now() + 60_000).toISOString();
        getOutboxQueue.mockReturnValue(of(queue([entry(1, { attempts: 2, nextAttemptAt: later })])));
        vi.advanceTimersByTime(2_000);

        const row = component['rows']()[0];
        expect(row.status).toBe('retrying');
        expect(row.attempts).toBe(2);
    });

    it('shows a row that left the queue as done, then drops it', () => {
        // The write was confirmed, so the server deleted the row; the view must not simply blink it away.
        getOutboxQueue.mockReturnValue(of(queue([entry(2)])));
        vi.advanceTimersByTime(2_000);

        const statuses = new Map(component['rows']().map((row) => [row.id, row.status]));
        expect(statuses.get(1)).toBe('done');
        expect(statuses.get(2)).toBe('queued');

        vi.advanceTimersByTime(4_000);
        expect(component['rows']().map((row) => row.id)).toEqual([2]);
    });

    it('does not resurrect a finished row while it is still lingering', () => {
        getOutboxQueue.mockReturnValue(of(queue([])));
        vi.advanceTimersByTime(2_000);
        expect(component['rows']().map((row) => row.status)).toEqual(['done', 'done']);

        // The same ids come back before the linger expires; they must not jump back to queued.
        getOutboxQueue.mockReturnValue(of(queue([entry(1), entry(2)])));
        vi.advanceTimersByTime(2_000);
        expect(component['rows']().every((row) => row.status === 'done')).toBe(true);
    });

    it('reports what the reconcile passes have done and how big the ledger is', () => {
        getReconcileStatus.mockReturnValue(
            of({
                passes: [{ pass: 'MISSING', positionEntityType: 'lecture', entitiesChecked: 66, repairsEnqueued: 66, rowsRemoved: 0, lastRunAt: new Date().toISOString() }],
                ledger: [
                    { entityType: 'course', count: 66 },
                    { entityType: 'lecture', count: 21 },
                ],
            }),
        );
        vi.advanceTimersByTime(2_000);

        expect(component['ledgerTotal']()).toBe(87);
        expect(component['reconcileNeverRan']()).toBe(false);
    });

    it('says so when no pass has ever run, which is what a dead reconciler looks like', () => {
        getReconcileStatus.mockReturnValue(of({ passes: [{ pass: 'MISSING', entitiesChecked: 0, repairsEnqueued: 0, rowsRemoved: 0 }], ledger: [] }));
        vi.advanceTimersByTime(2_000);

        expect(component['reconcileNeverRan']()).toBe(true);
    });

    it('keeps the queue readable when only the reconcile call fails', () => {
        getReconcileStatus.mockReturnValue(throwError(() => new Error('boom')));
        vi.advanceTimersByTime(2_000);

        expect(component['loadFailed']()).toBe(false);
        expect(component['rows']().map((row) => row.id)).toEqual([1, 2]);
    });

    it('reports a backlog larger than the returned page', () => {
        getOutboxQueue.mockReturnValue(of(queue([entry(1)], 500)));
        vi.advanceTimersByTime(2_000);

        expect(component['hasMore']()).toBe(true);
        expect(component['totalDepth']()).toBe(500);
    });

    it('surfaces a failed poll without clearing what it already showed', () => {
        getOutboxQueue.mockReturnValue(throwError(() => new Error('boom')));
        vi.advanceTimersByTime(2_000);

        expect(component['loadFailed']()).toBe(true);
        expect(component['rows']().map((row) => row.id)).toEqual([1, 2]);
    });

    it('names a bulk row by its operation, having no single entity', () => {
        getOutboxQueue.mockReturnValue(of(queue([entry(1, { entityType: undefined, entityId: undefined, operation: 'DELETE_COURSE' })])));
        vi.advanceTimersByTime(2_000);

        expect(component['subjectOf'](component['rows']().find((row) => row.id === 1)!)).toBe('DELETE_COURSE');
    });
});
