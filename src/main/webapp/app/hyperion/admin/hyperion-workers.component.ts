import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DOCUMENT, DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin, interval } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ActiveGeneration } from 'app/openapi/model/active-generation';
import { AdminHyperionGenerationMonitoringApi } from 'app/openapi/api/admin-hyperion-generation-monitoring-api';
import {
    TumUiButtonComponent,
    TumUiDialogComponent,
    TumUiInputDirective,
    TumUiMessageComponent,
    TumUiTableDirective,
    TumUiTagComponent,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { AdminHyperionWorkerApi } from 'app/openapi/api/admin-hyperion-worker-api';
import { GenerationWorkerStatus } from 'app/openapi/model/generation-worker-status';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Live worker capacity and exact-run administration, refreshed while the page is visible. */
@Component({
    selector: 'jhi-hyperion-workers',
    templateUrl: './hyperion-workers.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FormsModule,
        TumUiDialogComponent,
        TumUiInputDirective,
        TumUiTooltipDirective,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        DatePipe,
        TumUiButtonComponent,
        TumUiMessageComponent,
        TumUiTableDirective,
        TumUiTagComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class HyperionWorkersComponent implements OnInit {
    private readonly document = inject(DOCUMENT);
    private readonly api = inject(AdminHyperionWorkerApi);
    private readonly generationApi = inject(AdminHyperionGenerationMonitoringApi);
    protected readonly generations = signal<ActiveGeneration[]>([]);
    protected readonly generationRows = computed(() =>
        this.generations().map((run) => {
            const worker = this.workers().find((candidate) => candidate.executions?.some((execution) => execution.jobId === run.jobId));
            const execution = worker?.executions?.find((candidate) => candidate.jobId === run.jobId);
            const exerciseLink =
                run.courseId !== undefined && run.exerciseId !== undefined ? ['/course-management', run.courseId, 'programming-exercises', run.exerciseId] : undefined;
            return {
                run,
                workerId: worker?.workerId,
                slot: execution?.slot !== undefined ? execution.slot + 1 : undefined,
                exerciseLink,
                runLink: exerciseLink ? [...exerciseLink, 'generation'] : undefined,
            };
        }),
    );
    protected readonly cancelTarget = signal<ActiveGeneration | undefined>(undefined);
    protected readonly cancelDialogVisible = signal(false);
    protected readonly cancelReason = signal('');
    protected readonly cancelling = signal(false);
    protected readonly canSubmitCancellation = computed(
        () =>
            !!this.cancelReason().trim() &&
            this.cancelReason().trim().length <= 500 &&
            !this.cancelling() &&
            !this.failed() &&
            !!this.cancelTarget()?.cancellable &&
            !this.cancelTarget()?.cancellationRequested,
    );
    protected readonly cancelFailed = signal(false);
    protected readonly slots = computed(() => this.workers().reduce((total, worker) => total + (worker.capacity ?? 1), 0));
    private readonly destroyRef = inject(DestroyRef);
    protected readonly workers = signal<GenerationWorkerStatus[]>([]);
    protected readonly loading = signal(false);
    protected readonly failed = signal(false);
    protected readonly available = computed(() => this.workers().reduce((total, worker) => total + (worker.availableSlots ?? (worker.state === 'AVAILABLE' ? 1 : 0)), 0));

    protected readonly updatedAt = signal<Date | undefined>(undefined);
    protected readonly occupied = computed(() =>
        this.workers().reduce(
            (total, worker) =>
                total +
                (worker.state === 'OFFLINE' || worker.state === 'NOT_READY'
                    ? 0
                    : Math.max(0, (worker.capacity ?? 1) - (worker.availableSlots ?? (worker.state === 'AVAILABLE' ? 1 : 0)))),
            0,
        ),
    );
    protected readonly unavailable = computed(() => this.workers().filter((worker) => worker.state === 'OFFLINE' || worker.state === 'NOT_READY').length);
    protected readonly retainedReservations = computed(() => this.workers().filter((worker) => worker.state === 'OFFLINE' && worker.leaseHeld).length);
    protected readonly rows = computed(() =>
        this.workers().map((worker) => ({
            worker,
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

    protected selectCancellation(run: ActiveGeneration): void {
        this.cancelTarget.set(run);
        this.cancelReason.set('');
        this.cancelFailed.set(false);
        this.cancelDialogVisible.set(true);
    }

    protected cancelGeneration(): void {
        const run = this.cancelTarget();
        const reason = this.cancelReason().trim();
        if (!run?.exerciseId || !run.jobId || !run.cancellable || run.cancellationRequested || !reason || reason.length > 500 || this.cancelling() || this.failed()) {
            return;
        }
        this.cancelling.set(true);
        this.cancelFailed.set(false);
        this.generationApi
            .cancelGeneration(run.exerciseId, run.jobId, reason)
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.cancelling.set(false)),
            )
            .subscribe({
                next: () => {
                    this.cancelDialogVisible.set(false);
                    this.refresh();
                },
                error: () => {
                    this.cancelFailed.set(true);
                    this.refresh();
                },
            });
    }

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
        forkJoin({ workers: this.api.getGenerationWorkers(), generations: this.generationApi.getActiveGenerations() })
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.loading.set(false)),
            )
            .subscribe({
                next: ({ workers, generations }) => {
                    this.workers.set(workers);
                    this.generations.set(generations);
                    const selected = this.cancelTarget();
                    if (selected) {
                        this.cancelTarget.set(generations.find((run) => run.jobId === selected.jobId) ?? cloneWith(selected, { cancellable: false }));
                    }
                    this.updatedAt.set(new Date());
                },
                error: () => this.failed.set(true),
            });
    }
}
