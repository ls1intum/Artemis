import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TumAetUiMessageComponent, TumAetUiTableDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { WorkerStatus } from 'app/openapi/model/worker-status';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Operational capacity only; workload-specific administration stays in its feature. */
@Component({
    selector: 'jhi-ai-workers',
    templateUrl: './ai-workers.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DatePipe, TumAetUiMessageComponent, TumAetUiTableDirective, TumAetUiTagComponent, TumAetUiTooltipDirective, TranslateDirective, ArtemisTranslatePipe],
})
export class AiWorkersComponent {
    readonly workers = input<WorkerStatus[]>([]);
    readonly loading = input(false);
    readonly failed = input(false);
    readonly updatedAt = input<Date>();
    protected readonly slots = computed(() => this.workers().reduce((total, worker) => total + (worker.state === 'OFFLINE' ? 0 : (worker.capacity ?? 0)), 0));
    protected readonly available = computed(() => this.workers().reduce((total, worker) => total + (worker.state === 'AVAILABLE' ? (worker.availableSlots ?? 0) : 0), 0));

    protected readonly occupied = computed(() =>
        this.workers().reduce(
            (total, worker) => total + (worker.state === 'OFFLINE' || worker.state === 'NOT_READY' ? 0 : Math.max(0, (worker.capacity ?? 0) - (worker.availableSlots ?? 0))),
            0,
        ),
    );
    protected readonly unavailable = computed(() => this.workers().filter((worker) => worker.state === 'OFFLINE' || worker.state === 'NOT_READY').length);
    protected readonly retainedReservations = computed(() => this.workers().filter((worker) => worker.state === 'OFFLINE' && worker.leaseHeld).length);
    protected readonly rows = computed(() =>
        this.workers().map((worker) => ({
            worker,
            capacityKnown: worker.state !== 'OFFLINE' && worker.capacity !== undefined && worker.availableSlots !== undefined,
            severity:
                worker.state === 'AVAILABLE'
                    ? ('success' as const)
                    : worker.state === 'BUSY'
                      ? ('info' as const)
                      : worker.state === 'RESERVED'
                        ? ('warn' as const)
                        : ('danger' as const),
        })),
    );
}
