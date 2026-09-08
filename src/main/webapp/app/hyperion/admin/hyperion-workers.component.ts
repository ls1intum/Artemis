import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TumUiButtonComponent, TumUiMessageComponent, TumUiTableDirective } from '@tumaet/ui-angular';
import { AdminHyperionWorkerApi } from 'app/openapi/api/admin-hyperion-worker-api';
import { GenerationWorkerStatus } from 'app/openapi/model/generation-worker-status';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Read-only, explicitly refreshed view of standalone worker capacity. */
@Component({
    selector: 'jhi-hyperion-workers',
    templateUrl: './hyperion-workers.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DatePipe, TumUiButtonComponent, TumUiMessageComponent, TumUiTableDirective, TranslateDirective, ArtemisTranslatePipe],
})
export class HyperionWorkersComponent implements OnInit {
    private readonly api = inject(AdminHyperionWorkerApi);
    private readonly destroyRef = inject(DestroyRef);
    protected readonly workers = signal<GenerationWorkerStatus[]>([]);
    protected readonly loading = signal(false);
    protected readonly failed = signal(false);
    protected readonly available = computed(() => this.workers().filter((worker) => worker.state === 'AVAILABLE').length);

    ngOnInit(): void {
        this.refresh();
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
                next: (workers) => this.workers.set(workers),
                error: () => this.failed.set(true),
            });
    }
}
