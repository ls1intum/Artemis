import { TumUiButtonComponent, TumUiDialogComponent, TumUiMessageComponent, TumUiTagComponent } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faCircleCheck, faRotate, faSpinner, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { GenerationSandboxJob } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { EMPTY, Observable, Subject, catchError, exhaustMap, map, merge, takeUntil, tap, timer } from 'rxjs';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { elapsedSecondsSince, generationModeLabelKey, serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';

@Component({
    selector: 'jhi-hyperion-generation-detail',
    templateUrl: './hyperion-generation-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FaIconComponent,
        TumUiButtonComponent,
        TumUiDialogComponent,
        TumUiMessageComponent,
        TumUiTagComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
        ArtemisTimeAgoPipe,
    ],
})
export class HyperionGenerationDetailComponent implements OnInit {
    private static readonly REFRESH_INTERVAL_MS = 5000;

    private readonly route = inject(ActivatedRoute);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly destroyRef = inject(DestroyRef);

    readonly job = signal<GenerationSandboxJob | undefined>(undefined);
    readonly loading = signal(false);
    readonly loadFailed = signal(false);
    readonly notFound = signal(false);
    readonly canceling = signal(false);
    readonly cancelFailed = signal(false);
    readonly backgroundRefreshFailed = signal(false);
    readonly cancellationRequested = signal(false);
    readonly released = signal(false);
    readonly naturallyEnded = signal(false);
    readonly confirmCancelVisible = signal(false);

    readonly jobId = this.route.snapshot.paramMap.get('jobId') ?? '';
    readonly agentName = this.route.snapshot.queryParamMap.get('agentName') ?? '';
    readonly faRotate = faRotate;
    readonly faSpinner = faSpinner;
    readonly faCircleCheck = faCircleCheck;
    readonly faTriangleExclamation = faTriangleExclamation;

    private readonly generationEnded = new Subject<void>();

    private readonly loadRequests = new Subject<boolean>();

    private readonly now = serverTimeSignal(this.generationEnded);

    readonly modeLabelKey = computed(() => {
        const job = this.job();
        return job ? generationModeLabelKey(job.mode) : undefined;
    });

    readonly elapsedSeconds = computed(() => {
        const job = this.job();
        return job ? elapsedSecondsSince(job.startedAt, this.now()) : 0;
    });

    /** The sandbox container id: the session id without the {@code <agent>::} affinity prefix the server encodes into it. */
    readonly containerId = computed(() => {
        const sessionId = this.job()?.sessionId ?? '';
        const separatorIndex = sessionId.indexOf('::');
        return separatorIndex < 0 ? sessionId : sessionId.slice(separatorIndex + 2);
    });

    private initialLoadResolved = false;
    private readonly backToAgent = viewChild<ElementRef<HTMLAnchorElement>>('backToAgent');

    constructor() {
        effect(() => {
            if (this.released()) {
                this.backToAgent()?.nativeElement.focus();
            }
        });
    }

    ngOnInit(): void {
        if (!this.jobId || !this.agentName) {
            this.notFound.set(true);
            return;
        }
        merge(
            this.loadRequests,
            // Background refresh; it stops as soon as the generation reached a terminal state.
            timer(HyperionGenerationDetailComponent.REFRESH_INTERVAL_MS, HyperionGenerationDetailComponent.REFRESH_INTERVAL_MS).pipe(
                takeUntil(this.generationEnded),
                map(() => false),
            ),
        )
            .pipe(
                // Ignore refresh requests while one is in flight instead of cancelling the active request.
                exhaustMap((showLoading) => this.fetchJob(showLoading)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe();
        this.load();
    }

    load(showLoading = true): void {
        this.loadRequests.next(showLoading);
    }

    confirmCancel(): void {
        const job = this.job();
        if (!job || this.canceling()) {
            return;
        }
        this.confirmCancelVisible.set(true);
    }

    acceptCancel(): void {
        this.confirmCancelVisible.set(false);
        const job = this.job();
        if (!job || this.canceling()) {
            return;
        }
        this.cancel(job);
    }

    private fetchJob(showLoading: boolean): Observable<unknown> {
        this.loading.set(showLoading);
        this.loadFailed.set(false);
        this.backgroundRefreshFailed.set(false);
        if (showLoading) {
            this.notFound.set(false);
        }
        return this.buildAgentsService.getGenerationSandboxes(this.agentName).pipe(
            tap((jobs) => this.applyJobs(jobs)),
            catchError(() => {
                if (this.job()) {
                    this.backgroundRefreshFailed.set(true);
                } else {
                    this.loadFailed.set(true);
                }
                this.loading.set(false);
                return EMPTY;
            }),
        );
    }

    private applyJobs(jobs: GenerationSandboxJob[]): void {
        const job = jobs.find((candidate) => candidate.jobId === this.jobId);
        if (job) {
            this.job.set(cloneWith(job, { agentName: this.agentName }));
            this.notFound.set(false);
            this.initialLoadResolved = true;
        } else if (!this.initialLoadResolved) {
            this.notFound.set(true);
        } else if (this.cancellationRequested()) {
            this.released.set(true);
            this.generationEnded.next();
        } else {
            this.naturallyEnded.set(true);
            this.generationEnded.next();
        }
        this.loading.set(false);
    }

    private cancel(job: GenerationSandboxJob): void {
        this.canceling.set(true);
        this.cancelFailed.set(false);
        this.buildAgentsService
            .cancelGeneration(job.exerciseId, job.jobId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.canceling.set(false);
                    this.cancellationRequested.set(true);
                    this.load(false);
                },
                error: () => {
                    this.canceling.set(false);
                    this.cancelFailed.set(true);
                },
            });
    }
}
