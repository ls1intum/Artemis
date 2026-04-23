import { Component, computed, input, signal } from '@angular/core';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';

import { Thread, ThreadState } from 'app/core/admin/metrics/metrics.model';
import { MetricsModalThreadsComponent } from '../metrics-modal-threads/metrics-modal-threads.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-jvm-threads',
    templateUrl: './jvm-threads.component.html',
    imports: [TranslateDirective, NgbProgressbar, DecimalPipe, MetricsModalThreadsComponent],
})
export class JvmThreadsComponent {
    /** Thread data from parent */
    readonly threads = input<Thread[]>([]);

    /** Visibility of threads modal */
    showThreadsModal = signal(false);

    /** Computed thread statistics derived from threads input */
    readonly threadStats = computed(() => {
        let runnable = 0;
        let waiting = 0;
        let timedWaiting = 0;
        let blocked = 0;

        this.threads().forEach((thread) => {
            switch (thread.threadState) {
                case ThreadState.Runnable:
                    runnable += 1;
                    break;
                case ThreadState.Waiting:
                    waiting += 1;
                    break;
                case ThreadState.TimedWaiting:
                    timedWaiting += 1;
                    break;
                case ThreadState.Blocked:
                    blocked += 1;
                    break;
            }
        });

        return {
            all: runnable + waiting + timedWaiting + blocked,
            runnable,
            timedWaiting,
            waiting,
            blocked,
        };
    });

    open(): void {
        this.showThreadsModal.set(true);
    }
}
