import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

import { Thread, ThreadState } from '../../metrics.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiSelectButtonComponent, TumUiTableDirective, TumUiTagComponent } from '@tumaet/ui-angular';
type ThreadStateFilter = ThreadState | 'ALL';

interface ThreadStateFilterOption {
    label: string;
    value: ThreadStateFilter;
    severity: 'secondary' | 'success' | 'info' | 'warn' | 'danger';
    testId: string;
    count: () => number;
}

@Component({
    selector: 'jhi-thread-modal',
    templateUrl: './metrics-modal-threads.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        FormsModule,
        ArtemisTranslatePipe,
        TumUiDialogComponent,
        TumUiTagComponent,
        TumUiButtonComponent,
        TumUiInputDirective,
        TumUiTableDirective,
        TumUiSelectButtonComponent,
    ],
})
export class MetricsModalThreadsComponent {
    /** Active thread-state filter; 'ALL' shows every thread. */
    readonly selectedThreadState = signal<ThreadStateFilter>('ALL');

    readonly visible = model<boolean>(false);
    readonly threads = input<Thread[]>([]);

    private readonly _threadFilter = signal<string | undefined>(undefined);
    get threadFilter(): string | undefined {
        return this._threadFilter();
    }
    set threadFilter(value: string | undefined) {
        this._threadFilter.set(value);
    }

    readonly threadDumpRunnable = computed(() => this.threads().filter((t) => t.threadState === ThreadState.Runnable).length);
    readonly threadDumpWaiting = computed(() => this.threads().filter((t) => t.threadState === ThreadState.Waiting).length);
    readonly threadDumpTimedWaiting = computed(() => this.threads().filter((t) => t.threadState === ThreadState.TimedWaiting).length);
    readonly threadDumpBlocked = computed(() => this.threads().filter((t) => t.threadState === ThreadState.Blocked).length);
    readonly threadDumpAll = computed(() => this.threadDumpRunnable() + this.threadDumpWaiting() + this.threadDumpTimedWaiting() + this.threadDumpBlocked());

    readonly filterOptions: ThreadStateFilterOption[] = [
        { label: 'All', value: 'ALL', severity: 'secondary', testId: 'filter-all', count: this.threadDumpAll },
        { label: 'Runnable', value: ThreadState.Runnable, severity: 'success', testId: 'filter-runnable', count: this.threadDumpRunnable },
        { label: 'Waiting', value: ThreadState.Waiting, severity: 'info', testId: 'filter-waiting', count: this.threadDumpWaiting },
        { label: 'Timed Waiting', value: ThreadState.TimedWaiting, severity: 'warn', testId: 'filter-timed-waiting', count: this.threadDumpTimedWaiting },
        { label: 'Blocked', value: ThreadState.Blocked, severity: 'danger', testId: 'filter-blocked', count: this.threadDumpBlocked },
    ];

    readonly filteredThreads = computed(() => {
        return this.threads().filter((thread) => this.isMatchingTextFilter(thread) && this.isMatchingSelectedThreadState(thread));
    });

    getBadgeSeverity(threadState: ThreadState): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
        switch (threadState) {
            case ThreadState.Runnable:
                return 'success';
            case ThreadState.Waiting:
                return 'info';
            case ThreadState.TimedWaiting:
                return 'warn';
            case ThreadState.Blocked:
                return 'danger';
            default:
                return 'secondary';
        }
    }

    private isMatchingTextFilter(thread: Thread): boolean {
        const filter = this._threadFilter();
        if (filter == undefined) {
            return true;
        }

        // Filter the threads only on the visible attributes and look for case-insensitive match
        // Only the scalar (string/number) attributes are searchable; `as const` narrows the key union so
        // `thread[key]` is a scalar (not the full `keyof Thread` union, which includes object-typed fields).
        const filteredAttributes = ['threadName', 'threadId', 'blockedTime', 'blockedCount', 'waitedTime', 'waitedCount', 'lockName'] as const satisfies readonly (keyof Thread)[];
        return filteredAttributes.some((key) => thread[key]?.toString().toLowerCase().includes(filter.toLowerCase()));
    }

    private isMatchingSelectedThreadState(thread: Thread): boolean {
        const state = this.selectedThreadState();
        if (state === 'ALL') {
            return true;
        }

        return thread.threadState === state;
    }

    dismiss(): void {
        this.visible.set(false);
    }
}
