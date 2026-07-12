import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { faRotate, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { GenerationSandboxJob, groupGenerationSandboxSessions } from 'app/localci/shared/entities/generation-sandbox-session.model';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { Subscription } from 'rxjs';

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
    readonly now = signal(dayjs());

    readonly jobId = this.route.snapshot.paramMap.get('jobId') ?? '';
    readonly agentName = this.route.snapshot.queryParamMap.get('agentName') ?? '';
    readonly faRotate = faRotate;
    readonly faSpinner = faSpinner;

    private durationInterval?: ReturnType<typeof setInterval>;
    private refreshInterval?: ReturnType<typeof setInterval>;
    private loadSubscription?: Subscription;

    ngOnInit(): void {
        if (!this.jobId || !this.agentName) {
            this.notFound.set(true);
            return;
        }
        this.load();
        this.durationInterval = setInterval(() => this.now.set(dayjs()), 1000);
        this.refreshInterval = setInterval(() => this.load(false), 5000);
    }

    ngOnDestroy(): void {
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
        if (showLoading) {
            this.notFound.set(false);
        }
        this.loadSubscription = this.buildAgentsService.getGenerationSandboxes(this.agentName).subscribe({
            next: (sessions) => {
                const job = groupGenerationSandboxSessions(sessions).find((candidate) => candidate.jobId === this.jobId);
                this.job.set(job);
                this.notFound.set(!job);
                this.loading.set(false);
            },
            error: () => {
                this.job.set(undefined);
                this.loadFailed.set(true);
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
            message: this.translateService.instant('artemisApp.buildAgents.generationSandboxes.cancelQuestion', { exerciseId: job.exerciseId }),
            icon: 'pi pi-exclamation-triangle',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary', outlined: true },
            defaultFocus: 'reject',
            accept: () => this.cancel(job),
        });
    }

    duration(startedAt: string): string {
        const seconds = Math.max(0, this.now().diff(dayjs(startedAt), 'seconds'));
        const minutes = Math.floor(seconds / 60);
        return minutes > 0 ? `${minutes}m ${seconds % 60}s` : `${seconds}s`;
    }

    shortSessionId(sessionId: string): string {
        return sessionId.length > 16 ? `${sessionId.slice(0, 12)}…` : sessionId;
    }

    private cancel(job: GenerationSandboxJob): void {
        this.canceling.set(true);
        this.cancelFailed.set(false);
        this.buildAgentsService.cancelGeneration(job.exerciseId, job.jobId).subscribe({
            next: () => {
                this.canceling.set(false);
                this.load(false);
            },
            error: () => {
                this.canceling.set(false);
                this.cancelFailed.set(true);
            },
        });
    }
}
