import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

import { Thread, ThreadState } from '../../metrics.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';

@Component({
    selector: 'jhi-thread-modal',
    templateUrl: './metrics-modal-threads.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FaIconComponent, FormsModule, ArtemisTranslatePipe, DialogModule, TagModule, ButtonModule, InputTextModule, TableModule],
})
export class MetricsModalThreadsComponent {
    ThreadState = ThreadState;

    private threadStateFilter = signal<ThreadState | undefined>(undefined);
    get selectedThreadState(): ThreadState | undefined {
        return this.threadStateFilter();
    }

    readonly visible = model<boolean>(false);
    readonly threads = input<Thread[]>([]);

    set selectedThreadState(newValue: ThreadState | undefined) {
        this.threadStateFilter.set(newValue);
    }

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

    readonly filteredThreads = computed(() => {
        return this.threads().filter((thread) => this.isMatchingTextFilter(thread) && this.isMatchingSelectedThreadState(thread));
    });

    // Icons
    faCheck = faCheck;

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
        const filteredAttributes = ['threadName', 'threadId', 'blockedTime', 'blockedCount', 'waitedTime', 'waitedCount', 'lockName'];
        return Object.keys(thread)
            .filter((key) => filteredAttributes.includes(key))
            .some((key) => thread[key as keyof Thread]?.toString().toLowerCase().includes(filter.toLowerCase()));
    }

    private isMatchingSelectedThreadState(thread: Thread): boolean {
        const state = this.threadStateFilter();
        if (state == undefined) {
            return true;
        }

        return thread.threadState === state;
    }

    dismiss(): void {
        this.visible.set(false);
    }
}
