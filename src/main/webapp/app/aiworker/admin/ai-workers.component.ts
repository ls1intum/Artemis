import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DOCUMENT, DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, interval } from 'rxjs';
import { TumAetUiButtonComponent, TumAetUiMessageComponent, TumAetUiTableDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { AdminAiWorkerApi } from 'app/openapi/api/admin-ai-worker-api';
import { WorkerStatus } from 'app/openapi/model/worker-status';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Operational capacity only; workload-specific administration stays in its feature. */
@Component({
    selector: 'jhi-ai-workers',
    templateUrl: './ai-workers.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DatePipe,
        TumAetUiButtonComponent,
        TumAetUiMessageComponent,
        TumAetUiTableDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class AiWorkersComponent implements OnInit {
    private readonly api = inject(AdminAiWorkerApi);
    private readonly destroyRef = inject(DestroyRef);
    private readonly document = inject(DOCUMENT);
    protected readonly workers = signal<WorkerStatus[]>([]);
    protected readonly loading = signal(false);
    protected readonly failed = signal(false);
    protected readonly updatedAt = signal<Date | undefined>(undefined);
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
            capability: worker.capability ? `${worker.capability.workload} / ${worker.capability.profile} (v${worker.capability.version})` : '—',
            capacityKnown: worker.state !== 'OFFLINE' && worker.capacity !== undefined && worker.availableSlots !== undefined,
            executions: (worker.executions ?? []).map((execution) => ({ id: execution.executionId, slot: execution.slot === undefined ? undefined : execution.slot + 1 })),
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
    ngOnInit(): void {
        this.refresh();
        interval(15_000)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                if (this.document.visibilityState === 'visible') {
                    this.refresh();
                }
            });
    }
    protected refresh(): void {
        if (this.loading()) {
            return;
        }
        this.loading.set(true);
        this.failed.set(false);
        this.api
            .getWorkers()
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.loading.set(false)),
            )
            .subscribe({
                next: (workers) => {
                    this.workers.set(workers);
                    this.updatedAt.set(new Date());
                },
                error: () => this.failed.set(true),
            });
    }
}
