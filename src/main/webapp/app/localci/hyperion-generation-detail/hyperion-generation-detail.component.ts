import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faCircleCheck, faRotate, faSpinner, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { GenerationSandboxJob } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { EMPTY, Observable, Subject, catchError, exhaustMap, interval, map, merge, takeUntil, tap, timer } from 'rxjs';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';

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
    /** How often the sandbox list is polled while the generation is still running. */
    private static readonly REFRESH_INTERVAL_MS = 5000;

    /** How often the elapsed-duration display re-renders. */
    private static readonly CLOCK_INTERVAL_MS = 1000;

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

    /** Emits once when the generation reached a terminal state; stops both the background refresh and the elapsed-time clock. */
    private readonly generationEnded = new Subject<void>();

    /** Manual (re)load requests from the retry buttons and from a completed cancellation; the value is the `showLoading` flag. */
    private readonly loadRequests = new Subject<boolean>();

    /**
     * Ticks once per second so the elapsed duration re-renders. It stops for good when the generation ends, which freezes
     * the signal on its last value and therefore freezes the displayed duration.
     * NOTE: for clock-skew correctness this should read `ArtemisServerDateService.now()` instead of the raw client clock;
     * that behaviour is deliberately left unchanged here.
     */
    private readonly now = toSignal(
        interval(HyperionGenerationDetailComponent.CLOCK_INTERVAL_MS).pipe(
            takeUntil(this.generationEnded),
            map(() => Date.now()),
        ),
        { initialValue: Date.now() },
    );

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
                // exhaustMap, not switchMap: a slow refresh must not be cancelled and re-fired. It also subsumes the
                // former re-entrancy guard, as further requests are ignored while one is still in flight.
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

    elapsedSeconds(timestamp: string): number {
        return Math.max(0, Math.floor((this.now() - Date.parse(timestamp)) / 1000));
    }

    containerId(sessionId: string): string {
        return sessionId.includes('::') ? sessionId.slice(sessionId.indexOf('::') + 2) : sessionId;
    }

    modeKey(mode: GenerationSandboxJob['mode']): string {
        return `artemisApp.buildAgents.generationSandboxes.${mode === 'ADAPT' ? 'adapt' : 'generate'}`;
    }

    /**
     * Requests the sandbox list once. Errors are handled inside this inner pipe so that a failing request can never
     * complete the outer polling stream.
     */
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
            this.job.set({ ...job, agentName: this.agentName });
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
        this.buildAgentsService.cancelGeneration(job.exerciseId, job.jobId).subscribe({
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
