/**
 * Vitest tests for MetricsModalThreadsComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

import { MetricsModalThreadsComponent } from 'app/core/admin/metrics/blocks/metrics-modal-threads/metrics-modal-threads.component';
import { Thread, ThreadState } from 'app/core/admin/metrics/metrics.model';

describe('MetricsModalThreadsComponent', () => {
    setupTestBed({ zoneless: true });

    let runnableThreads: Thread[];
    let waitingThreads: Thread[];

    let comp: MetricsModalThreadsComponent;
    let fixture: ComponentFixture<MetricsModalThreadsComponent>;
    let activeModal: NgbActiveModal;

    function createThread(threadId: number, threadState: ThreadState): Thread {
        return {
            threadName: '',
            threadId,
            blockedTime: -1,
            blockedCount: -1,
            waitedTime: -1,
            waitedCount: -1,
            lockName: undefined,
            lockOwnerId: -1,
            lockOwnerName: undefined,
            daemon: false,
            inNative: false,
            suspended: false,
            threadState,
            priority: -1,
            stackTrace: [],
            lockedMonitors: [],
            lockedSynchronizers: [],
            lockInfo: undefined,
        };
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsModalThreadsComponent],
            providers: [NgbActiveModal],
        })
            .overrideTemplate(MetricsModalThreadsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsModalThreadsComponent);
        comp = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);

        runnableThreads = [createThread(1, ThreadState.Runnable), createThread(2, ThreadState.Runnable), createThread(3, ThreadState.Runnable)];
        waitingThreads = [createThread(4, ThreadState.Waiting), createThread(5, ThreadState.Waiting), createThread(6, ThreadState.Waiting), createThread(7, ThreadState.Waiting)];
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    describe('onInit', () => {
        it('should count all thread types', () => {
            comp.threads = runnableThreads.concat(waitingThreads);

            comp.ngOnInit();

            expect(comp.threadDumpAll).toBe(comp.threads.length);
            expect(comp.threadDumpBlocked).toBe(0);
            expect(comp.threadDumpRunnable).toBe(runnableThreads.length);
            expect(comp.threadDumpTimedWaiting).toBe(0);
            expect(comp.threadDumpWaiting).toBe(waitingThreads.length);
        });
    });

    describe('background class', () => {
        it('should compute correct bg-* class based on thread state', () => {
            expect(comp.getBgClass(ThreadState.Runnable)).toBe('bg-success');
            expect(comp.getBgClass(ThreadState.Waiting)).toBe('bg-info');
            expect(comp.getBgClass(ThreadState.TimedWaiting)).toBe('bg-warning');
            expect(comp.getBgClass(ThreadState.Blocked)).toBe('bg-danger');
            expect(comp.getBgClass(ThreadState.New)).toBe('');
            expect(comp.getBgClass(ThreadState.Terminated)).toBe('');
        });
    });

    describe('filters', () => {
        it('should return all threads when no filter is applied', () => {
            comp.threads = runnableThreads.concat(waitingThreads);
            comp.selectedThreadState = undefined;

            comp.refreshFilteredThreads();

            expect(comp.filteredThreads).toEqual(runnableThreads.concat(waitingThreads));
        });

        it('should filter threads by selected thread state', () => {
            comp.threads = runnableThreads.concat(waitingThreads);
            comp.selectedThreadState = ThreadState.Runnable;

            comp.refreshFilteredThreads();

            expect(comp.filteredThreads).toEqual(runnableThreads);
        });

        it('should return all threads when filter text is undefined', () => {
            comp.threads = runnableThreads.concat(waitingThreads);
            comp.threadFilter = undefined;

            comp.refreshFilteredThreads();

            expect(comp.filteredThreads).toEqual(runnableThreads.concat(waitingThreads));
        });

        it('should filter threads by filter text', () => {
            comp.threads = runnableThreads.concat(waitingThreads);
            comp.threadFilter = '2';

            comp.refreshFilteredThreads();

            expect(comp.filteredThreads).toEqual([runnableThreads[1]]);
        });

        it('should filter by both thread state and filter text', () => {
            comp.threads = runnableThreads.concat(waitingThreads);
            comp.threadFilter = '2';
            comp.selectedThreadState = ThreadState.Waiting;

            comp.refreshFilteredThreads();

            expect(comp.filteredThreads).toEqual([]);
        });
    });

    describe('dismiss', () => {
        it('should call activeModal.dismiss()', () => {
            const dismissSpy = vi.spyOn(activeModal, 'dismiss').mockImplementation(() => {});

            comp.dismiss();

            expect(dismissSpy).toHaveBeenCalledOnce();
        });
    });
});
