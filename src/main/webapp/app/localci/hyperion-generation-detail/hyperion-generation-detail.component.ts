import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faCircleCheck, faRotate, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TranslateService } from '@ngx-translate/core';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { GenerationSandboxJob, groupGenerationSandboxSessions } from 'app/localci/shared/entities/generation-sandbox-session.model';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { Subscription } from 'rxjs';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-hyperion-generation-detail',
    templateUrl: './hyperion-generation-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FaIconComponent,
        ButtonModule,
        ConfirmDialogModule,
        MessageModule,
        TableModule,
        TagModule,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
    ],
    providers: [ConfirmationService],
})
export class HyperionGenerationDetailComponent implements OnInit, OnDestroy {
    private readonly route = inject(ActivatedRoute);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly confirmationService = inject(ConfirmationService);
    private readonly translateService = inject(TranslateService);

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
    readonly now = signal(Date.now());

    readonly jobId = this.route.snapshot.paramMap.get('jobId') ?? '';
    readonly agentName = this.route.snapshot.queryParamMap.get('agentName') ?? '';
    readonly faRotate = faRotate;
    readonly faSpinner = faSpinner;
    readonly faCircleCheck = faCircleCheck;

    private durationInterval?: ReturnType<typeof setInterval>;
    private refreshInterval?: ReturnType<typeof setInterval>;
    private loadSubscription?: Subscription;
    private initialLoadResolved = false;
    private readonly backToAgent = viewChild<ElementRef<HTMLAnchorElement>>('backToAgent');

    private readonly focusTerminalState = effect(() => {
        if (this.released()) {
            this.backToAgent()?.nativeElement.focus();
        }
    });

    ngOnInit(): void {
        if (!this.jobId || !this.agentName) {
            this.notFound.set(true);
            return;
        }
        this.load();
        this.durationInterval = setInterval(() => this.now.set(Date.now()), 1000);
        this.refreshInterval = setInterval(() => this.load(false), 5000);
    }

    ngOnDestroy(): void {
        this.focusTerminalState.destroy();
        if (this.durationInterval) {
            clearInterval(this.durationInterval);
        }
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
        }
        this.loadSubscription?.unsubscribe();
    }

    load(showLoading = true): void {
        this.loadSubscription?.unsubscribe();
        this.loading.set(showLoading);
        this.loadFailed.set(false);
        this.backgroundRefreshFailed.set(false);
        if (showLoading) {
            this.notFound.set(false);
        }
        this.loadSubscription = this.buildAgentsService.getGenerationSandboxes(this.agentName).subscribe({
            next: (sessions) => {
                const job = groupGenerationSandboxSessions(sessions).find((candidate) => candidate.jobId === this.jobId);
                if (job) {
                    this.job.set({ ...job, agentName: this.agentName });
                    this.notFound.set(false);
                    this.initialLoadResolved = true;
                } else if (!this.initialLoadResolved) {
                    this.notFound.set(true);
                } else if (this.cancellationRequested()) {
                    this.released.set(true);
                    this.stopRefresh();
                } else {
                    this.naturallyEnded.set(true);
                    this.stopRefresh();
                }
                this.loading.set(false);
            },
            error: () => {
                if (this.job()) {
                    this.backgroundRefreshFailed.set(true);
                } else {
                    this.loadFailed.set(true);
                }
                this.loading.set(false);
            },
        });
    }

    confirmCancel(): void {
        const job = this.job();
        if (!job || this.canceling()) {
            return;
        }
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.buildAgents.generationSandboxes.cancelTitle'),
            message: this.translateService.instant('artemisApp.buildAgents.generationSandboxes.cancelQuestion', {
                exerciseId: job.exerciseId,
                userLogin: job.userLogin,
                sessionCount: job.sessions.length,
            }),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: this.translateService.instant('artemisApp.buildAgents.generationSandboxes.confirmCancel'),
            rejectLabel: this.translateService.instant('artemisApp.buildAgents.generationSandboxes.keepRunning'),
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary', outlined: true },
            defaultFocus: 'reject',
            accept: () => this.cancel(job),
        });
    }

    elapsedSeconds(timestamp: string): number {
        return Math.max(0, Math.floor((this.now() - Date.parse(timestamp)) / 1000));
    }

    shortSessionId(sessionId: string): string {
        const containerId = this.containerId(sessionId);
        return containerId.length > 16 ? `${containerId.slice(0, 12)}…` : containerId;
    }

    containerId(sessionId: string): string {
        return sessionId.includes('::') ? sessionId.slice(sessionId.indexOf('::') + 2) : sessionId;
    }

    modeKey(mode: GenerationSandboxJob['mode']): string {
        return `artemisApp.buildAgents.generationSandboxes.${mode === 'ADAPT' ? 'adapt' : 'generate'}`;
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

    private stopRefresh(): void {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = undefined;
        }
    }
}
