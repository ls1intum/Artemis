import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Thread, ThreadState } from '../../metrics.model';

@Component({
    selector: 'jhi-thread-modal',
    templateUrl: './metrics-modal-threads.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricsModalThreadsComponent implements OnInit {
    ThreadState = ThreadState;

    private threadStateFilter?: ThreadState;
    get selectedThreadState(): ThreadState | undefined {
        return this.threadStateFilter;
    }
    set selectedThreadState(newValue: ThreadState | undefined) {
        this.threadStateFilter = newValue;
        this.refreshFilteredThreads();
    }

    threadFilter?: string;

    threads?: Thread[];
    filteredThreads: Thread[];

    threadDumpAll = 0;
    threadDumpBlocked = 0;
    threadDumpRunnable = 0;
    threadDumpTimedWaiting = 0;
    threadDumpWaiting = 0;

    // Icons
    faCheck = faCheck;

    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        this.threads?.forEach((thread) => {
            switch (thread.threadState) {
                case ThreadState.Runnable:
                    this.threadDumpRunnable += 1;
                    break;
                case ThreadState.Waiting:
                    this.threadDumpWaiting += 1;
                    break;
                case ThreadState.TimedWaiting:
                    this.threadDumpTimedWaiting += 1;
                    break;
                case ThreadState.Blocked:
                    this.threadDumpBlocked += 1;
                    break;
                default:
                    break;
            }
        });

        this.threadDumpAll = this.threadDumpRunnable + this.threadDumpWaiting + this.threadDumpTimedWaiting + this.threadDumpBlocked;
        this.filteredThreads = this.threads ?? [];
    }

    getBgClass(threadState: ThreadState): string {
        switch (threadState) {
            case ThreadState.Runnable:
                return 'bg-success';
            case ThreadState.Waiting:
                return 'bg-info';
            case ThreadState.TimedWaiting:
                return 'bg-warning';
            case ThreadState.Blocked:
                return 'bg-danger';
            default:
                return '';
        }
    }

    private isMatchingTextFilter(thread: Thread): boolean {
        if (this.threadFilter == undefined) {
            return true;
        }

        // Filter the threads only on the visible attributes and look for case-insensitive match
        const filteredAttributes = ['threadName', 'threadId', 'blockedTime', 'blockedCount', 'waitedTime', 'waitedCount', 'lockName'];
        return Object.keys(thread)
            .filter((key) => filteredAttributes.includes(key))
            .some((key) => thread[key as keyof Thread]?.toString().toLowerCase().includes(this.threadFilter!.toLowerCase()));
    }

    private isMatchingSelectedThreadState(thread: Thread): boolean {
        if (this.selectedThreadState == undefined) {
            return true;
        }

        return thread.threadState === this.selectedThreadState!;
    }

    refreshFilteredThreads() {
        this.filteredThreads = this.threads?.filter((thread) => this.isMatchingTextFilter(thread) && this.isMatchingSelectedThreadState(thread)) ?? [];
    }

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
