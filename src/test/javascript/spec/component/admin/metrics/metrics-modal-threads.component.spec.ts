import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MetricsModalThreadsComponent } from 'app/admin/metrics/blocks/metrics-modal-threads/metrics-modal-threads.component';
import { Thread, ThreadState } from 'app/admin/metrics/metrics.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

describe('MetricsModalThreadsComponent', () => {
    let runnableThreads: Thread[];
    let waitingThreads: Thread[];

    let comp: MetricsModalThreadsComponent;
    let fixture: ComponentFixture<MetricsModalThreadsComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MetricsModalThreadsComponent],
        })
            .overrideTemplate(MetricsModalThreadsComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MetricsModalThreadsComponent);
                comp = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);

                runnableThreads = [createThread(1, ThreadState.Runnable), createThread(2, ThreadState.Runnable), createThread(3, ThreadState.Runnable)];
                waitingThreads = [
                    createThread(4, ThreadState.Waiting),
                    createThread(5, ThreadState.Waiting),
                    createThread(6, ThreadState.Waiting),
                    createThread(7, ThreadState.Waiting),
                ];
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    function createThread(threadId: number, threadState: ThreadState): Thread {
        return {
            threadName: '',
            threadId,
            blockedTime: -1,
            blockedCount: -1,
            waitedTime: -1,
            waitedCount: -1,
            lockName: null,
            lockOwnerId: -1,
            lockOwnerName: null,
            daemon: false,
            inNative: false,
            suspended: false,
            threadState,
            priority: -1,
            stackTrace: [],
            lockedMonitors: [],
            lockedSynchronizers: [],
            lockInfo: null,
        };
    }

    describe('onInit', () => {
        it('counts all thread types', () => {
            // GIVEN
            comp.threads = runnableThreads.concat(waitingThreads);

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(comp.threadDumpAll).toEqual(comp.threads.length);
            expect(comp.threadDumpBlocked).toBe(0);
            expect(comp.threadDumpRunnable).toEqual(runnableThreads.length);
            expect(comp.threadDumpTimedWaiting).toBe(0);
            expect(comp.threadDumpWaiting).toEqual(waitingThreads.length);
        });
    });

    describe('background class', () => {
        it('computes correct bg-* class based on thread state', () => {
            expect(comp.getBgClass(ThreadState.Runnable)).toBe('bg-success');
            expect(comp.getBgClass(ThreadState.Waiting)).toBe('bg-info');
            expect(comp.getBgClass(ThreadState.TimedWaiting)).toBe('bg-warning');
            expect(comp.getBgClass(ThreadState.Blocked)).toBe('bg-danger');
            expect(comp.getBgClass(ThreadState.New)).toBe('');
            expect(comp.getBgClass(ThreadState.Terminated)).toBe('');
        });
    });

    describe('filters', () => {
        describe('threads based on selected thread state', () => {
            it('when selected thread state is undefined, nothing is filtered out', () => {
                // GIVEN
                comp.threads = runnableThreads.concat(waitingThreads);
                comp.selectedThreadState = undefined;

                // WHEN
                comp.refreshFilteredThreads();

                // THEN
                expect(comp.filteredThreads).toEqual(runnableThreads.concat(waitingThreads));
            });

            it('when selected a specific thread state, only threads with specific thread state are returned', () => {
                // GIVEN
                comp.threads = runnableThreads.concat(waitingThreads);
                comp.selectedThreadState = ThreadState.Runnable;

                // WHEN
                comp.refreshFilteredThreads();

                // THEN
                expect(comp.filteredThreads).toEqual(runnableThreads);
            });
        });

        describe('threads based on filter text', () => {
            it('when filter is undefined, nothing is filtered out', () => {
                // GIVEN
                comp.threads = runnableThreads.concat(waitingThreads);
                comp.threadFilter = undefined;

                // WHEN
                comp.refreshFilteredThreads();

                // THEN
                expect(comp.filteredThreads).toEqual(runnableThreads.concat(waitingThreads));
            });

            it('when filter is entered, only matching results are returned', () => {
                // GIVEN
                comp.threads = runnableThreads.concat(waitingThreads);
                comp.threadFilter = '2';

                // WHEN
                comp.refreshFilteredThreads();

                // THEN
                expect(comp.filteredThreads).toEqual([runnableThreads[1]]);
            });
        });

        it('both on thread state and filter text', () => {
            // GIVEN
            comp.threads = runnableThreads.concat(waitingThreads);
            comp.threadFilter = '2';
            comp.selectedThreadState = ThreadState.Waiting;

            // WHEN
            comp.refreshFilteredThreads();

            // THEN
            expect(comp.filteredThreads).toEqual([]);
        });
    });

    describe('on dismiss', () => {
        it('calls activeModal.dismiss()', () => {
            // GIVEN
            const dismissSpy = jest.spyOn(activeModal, 'dismiss').mockImplementation(() => {});
            expect(dismissSpy).not.toHaveBeenCalled();

            // WHEN
            comp.dismiss();

            // THEN
            expect(dismissSpy).toHaveBeenCalled();
        });
    });
});
