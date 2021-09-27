import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Thread, ThreadState } from '../../metrics.model';

@Component({
    selector: 'jhi-thread-modal',
    templateUrl: './metrics-modal-threads.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricsModalThreadsComponent implements OnInit {
    ThreadState = ThreadState;
    threadStateFilter?: string;
    threads?: Thread[];
    threadDumpAll = 0;
    threadDumpBlocked = 0;
    threadDumpRunnable = 0;
    threadDumpTimedWaiting = 0;
    threadDumpWaiting = 0;

    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        this.threads?.forEach((thread) => {
            if (thread.threadState === ThreadState.Runnable) {
                this.threadDumpRunnable += 1;
            } else if (thread.threadState === ThreadState.Waiting) {
                this.threadDumpWaiting += 1;
            } else if (thread.threadState === ThreadState.TimedWaiting) {
                this.threadDumpTimedWaiting += 1;
            } else if (thread.threadState === ThreadState.Blocked) {
                this.threadDumpBlocked += 1;
            }
        });

        this.threadDumpAll = this.threadDumpRunnable + this.threadDumpWaiting + this.threadDumpTimedWaiting + this.threadDumpBlocked;
    }

    getBadgeClass(threadState: ThreadState): string {
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

    getThreads(): Thread[] {
        return this.threads?.filter((thread) => !this.threadStateFilter || thread.lockName?.includes(this.threadStateFilter)) ?? [];
    }

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
