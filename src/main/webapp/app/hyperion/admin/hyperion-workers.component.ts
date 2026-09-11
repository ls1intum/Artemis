import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DOCUMENT, DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, interval } from 'rxjs';
import { TumUiButtonComponent, TumUiMessageComponent, TumUiTableDirective, TumUiTagComponent } from '@tumaet/ui-angular';
import { AdminHyperionWorkerApi } from 'app/openapi/api/admin-hyperion-worker-api';
import { GenerationWorkerStatus } from 'app/openapi/model/generation-worker-status';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Read-only worker capacity and ownership diagnostics, refreshed while the page is visible. */
@Component({
    selector: 'jhi-hyperion-workers',
    templateUrl: './hyperion-workers.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
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
    private readonly destroyRef = inject(DestroyRef);
    protected readonly workers = signal<GenerationWorkerStatus[]>([]);
    protected readonly loading = signal(false);
    protected readonly failed = signal(false);
    protected readonly available = computed(() => this.workers().filter((worker) => worker.state === 'AVAILABLE').length);

    protected readonly updatedAt = signal<Date | undefined>(undefined);
    protected readonly occupied = computed(() => this.workers().filter((worker) => worker.state === 'BUSY' || worker.state === 'RESERVED').length);
    protected readonly unavailable = computed(() => this.workers().length - this.available() - this.occupied());
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
            .getGenerationWorkers()
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
