import { WebsocketService } from 'app/foundation/service/websocket.service';
import { AiWorkersComponent } from 'app/aiworker/admin/ai-workers.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, catchError, finalize, forkJoin, of } from 'rxjs';
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
import { AdminAiWorkerApi } from 'app/openapi/api/admin-ai-worker-api';
import { WorkerStatus } from 'app/openapi/model/worker-status';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const MAX_CANCELLATION_REASON_LENGTH = 500;

/** Live worker capacity and exact-run administration, refreshed while the page is visible. */
@Component({
    selector: 'jhi-hyperion-workers',
    templateUrl: './hyperion-generations.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        AiWorkersComponent,
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
export class HyperionGenerationsComponent implements OnInit {
    protected readonly generationEnabled = inject(ProfileService).isModuleFeatureActive(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION);
    private readonly websocket = inject(WebsocketService);
    private readonly api = inject(AdminAiWorkerApi);
    private readonly generationApi = inject(AdminHyperionGenerationMonitoringApi);
    private readonly destroyRef = inject(DestroyRef);
    protected readonly workersFailed = signal(false);
    protected readonly generations = signal<ActiveGeneration[]>([]);
    protected readonly generationRows = computed(() =>
        this.generations().map((run) => {
            const worker = this.workers().find((candidate) => candidate.executions?.some((execution) => execution.jobId === run.jobId));
            const exerciseLink =
                run.courseId !== undefined && run.exerciseId !== undefined ? ['/course-management', run.courseId, 'programming-exercises', run.exerciseId] : undefined;
            return {
                run,
                workerId: worker?.workerId,
                exerciseLink,
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
            this.cancelReason().trim().length <= MAX_CANCELLATION_REASON_LENGTH &&
            !this.cancelling() &&
            !this.failed() &&
            !!this.cancelTarget()?.cancellable &&
            !this.cancelTarget()?.cancellationRequested,
    );
    protected readonly cancelFailed = signal(false);
    protected readonly workers = signal<WorkerStatus[]>([]);
    protected readonly loading = signal(false);
    protected readonly failed = signal(false);
    protected readonly workersUpdatedAt = signal<Date | undefined>(undefined);
    protected readonly generationsUpdatedAt = signal<Date | undefined>(undefined);
    protected readonly liveUpdatesPaused = signal(false);
    private workerRevision = 0;
    private generationRevision = 0;
    private refreshSubscription?: Subscription;

    protected selectCancellation(run: ActiveGeneration): void {
        this.cancelTarget.set(run);
        this.cancelReason.set('');
        this.cancelFailed.set(false);
        this.cancelDialogVisible.set(true);
    }

    protected cancelGeneration(): void {
        const run = this.cancelTarget();
        const reason = this.cancelReason().trim();
        if (
            !run?.exerciseId ||
            !run.jobId ||
            !run.cancellable ||
            run.cancellationRequested ||
            !reason ||
            reason.length > MAX_CANCELLATION_REASON_LENGTH ||
            this.cancelling() ||
            this.failed()
        ) {
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
        this.websocket
            .subscribe<WorkerStatus[]>('/topic/admin/ai-workers')
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((workers) => {
                this.workerRevision++;
                this.workers.set(workers);
                this.workersFailed.set(false);
                this.workersUpdatedAt.set(new Date());
            });
        if (this.generationEnabled) {
            this.websocket
                .subscribe<ActiveGeneration[]>('/topic/admin/hyperion-generations')
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe((generations) => {
                    this.generationRevision++;
                    this.updateGenerations(generations);
                    this.failed.set(false);
                    this.generationsUpdatedAt.set(new Date());
                });
        }
        this.websocket.connectionState.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((state) => {
            if (state.connected) {
                if (this.liveUpdatesPaused()) this.refresh();
                this.liveUpdatesPaused.set(false);
            } else if (state.wasEverConnectedBefore) {
                this.liveUpdatesPaused.set(true);
            }
        });
        this.refresh();
    }

    private updateGenerations(generations: ActiveGeneration[]): void {
        this.generations.set(generations);
        const selected = this.cancelTarget();
        if (selected) this.cancelTarget.set(generations.find((run) => run.jobId === selected.jobId) ?? cloneWith(selected, { cancellable: false }));
    }

    protected refresh(): void {
        this.refreshSubscription?.unsubscribe();
        const workerRevision = this.workerRevision;
        const generationRevision = this.generationRevision;
        this.loading.set(true);
        this.refreshSubscription = forkJoin({
            workers: this.api.getWorkers().pipe(
                catchError(() => {
                    if (this.workerRevision === workerRevision) this.workersFailed.set(true);
                    return of(undefined);
                }),
            ),
            generations: this.generationEnabled
                ? this.generationApi.getActiveGenerations().pipe(
                      catchError(() => {
                          if (this.generationRevision === generationRevision) this.failed.set(true);
                          return of(undefined);
                      }),
                  )
                : of(undefined),
        })
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.loading.set(false)),
            )
            .subscribe({
                next: ({ workers, generations }) => {
                    if (workers && this.workerRevision === workerRevision) {
                        this.workers.set(workers);
                        this.workersFailed.set(false);
                        this.workersUpdatedAt.set(new Date());
                    }
                    if (generations && this.generationRevision === generationRevision) {
                        this.updateGenerations(generations);
                        this.failed.set(false);
                        this.generationsUpdatedAt.set(new Date());
                    }
                },
                error: () => this.failed.set(true),
            });
    }
}
