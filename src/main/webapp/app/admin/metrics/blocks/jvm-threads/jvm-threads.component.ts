import { ChangeDetectionStrategy, ChangeDetectorRef, Component, effect, inject, input, signal } from '@angular/core';
import { NgbModal, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';

import { Thread, ThreadState } from 'app/admin/metrics/metrics.model';
import { MetricsModalThreadsComponent } from '../metrics-modal-threads/metrics-modal-threads.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-jvm-threads',
    templateUrl: './jvm-threads.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [TranslateDirective, NgbProgressbar, DecimalPipe],
})
export class JvmThreadsComponent {
    threadStats = signal({
        all: 0,
        runnable: 0,
        timedWaiting: 0,
        waiting: 0,
        blocked: 0,
    });

    private changeDetector = inject(ChangeDetectorRef);
    private modalService = inject(NgbModal);

    threads = input<Thread[]>([]);

    constructor() {
        effect(() => this.computeThreadStats());
    }

    private computeThreadStats() {
        this.threads().forEach((thread) => {
            switch (thread.threadState) {
                case ThreadState.Runnable:
                    this.threadStats().runnable += 1;
                    break;
                case ThreadState.Waiting:
                    this.threadStats().waiting += 1;
                    break;
                case ThreadState.TimedWaiting:
                    this.threadStats().timedWaiting += 1;
                    break;
                case ThreadState.Blocked:
                    this.threadStats().blocked += 1;
                    break;
            }
        });

        this.threadStats().all = this.threadStats().runnable + this.threadStats().waiting + this.threadStats().timedWaiting + this.threadStats().blocked;
        this.changeDetector.markForCheck();
    }

    open(): void {
        const modalRef = this.modalService.open(MetricsModalThreadsComponent, { size: 'xl' });
        modalRef.componentInstance.threads = this.threads();
    }
}
