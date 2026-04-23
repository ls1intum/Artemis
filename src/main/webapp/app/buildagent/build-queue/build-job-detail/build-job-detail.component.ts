import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { faCircleCheck, faClock, faDownload, faExclamationCircle, faExclamationTriangle, faHourglass, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgClass } from '@angular/common';
import { ResultComponent } from 'app/exercise/result/result.component';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { downloadFile } from 'app/shared/util/download.util';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';
import { createAddressToAgentInfoMap, getAgentInfoByAddress } from 'app/buildagent/shared/build-agent-address.utils';

@Component({
    selector: 'jhi-build-job-detail',
    templateUrl: './build-job-detail.component.html',
    styleUrl: './build-job-detail.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FaIconComponent, ArtemisDatePipe, NgClass, RouterLink, ResultComponent, AdminTitleBarTitleDirective, HelpIconComponent],
})
export class BuildJobDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private buildQueueService = inject(BuildOverviewService);
    private buildAgentsService = inject(BuildAgentsService);
    private websocketService = inject(WebsocketService);
    private alertService = inject(AlertService);

    protected readonly TriggeredByPushTo = TriggeredByPushTo;

    readonly faCircleCheck = faCircleCheck;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faDownload = faDownload;
    readonly faClock = faClock;
    readonly faHourglass = faHourglass;
    readonly faSpinner = faSpinner;

    /**
     * The build job data - either a BuildJob (queued/running) or FinishedBuildJob.
     * Uses 'any' type because BuildJob and FinishedBuildJob have incompatible structures
     * and this component accesses properties from both types dynamically.
     */

    buildJob = signal<any | undefined>(undefined);

    /** Build log content */
    buildLogs = signal<string>('');

    /** Whether logs are currently loading */
    isLoadingLogs = signal(false);

    /** Whether the initial data is loading */
    isLoading = signal(true);

    /** Whether the job is not found */
    notFound = signal(false);

    /** Course ID (0 for admin view) */
    courseId = 0;

    /** Build job ID from query params */
    buildJobId = '';

    /** WebSocket subscription */
    private wsSubscription?: Subscription;

    /** Interval for updating build duration */
    private durationInterval?: ReturnType<typeof setInterval>;

    /** Whether this is admin view */
    isAdministrationView = signal(false);

    /** Available build agents for address-to-name mapping */
    buildAgents = signal<BuildAgentInformation[]>([]);

    /** Computed mapping from build agent host to agent info (name and displayName) */
    addressToAgentInfoMap = computed(() => createAddressToAgentInfoMap(this.buildAgents()));

    /** Whether the job is finished */
    isFinished = computed(() => {
        const job = this.buildJob();
        if (!job) return false;
        // FinishedBuildJob has buildCompletionDate, BuildJob has status
        return (
            !!job.buildCompletionDate || job.status === 'SUCCESSFUL' || job.status === 'FAILED' || job.status === 'CANCELLED' || job.status === 'ERROR' || job.status === 'TIMEOUT'
        );
    });

    /** Computed queue wait time formatted for display */
    queueWaitTime = computed(() => {
        const job = this.buildJob();
        if (!job) return undefined;
        const submissionDate = job.buildSubmissionDate || job.jobTimingInfo?.submissionDate;
        const buildStartDate = job.buildStartDate || job.jobTimingInfo?.buildStartDate;
        if (submissionDate && buildStartDate) {
            // Clamp to 0 to handle potential clock skew or inconsistent timestamps
            const waitTimeSeconds = Math.max(0, dayjs(buildStartDate).diff(dayjs(submissionDate), 'milliseconds') / 1000);
            return this.formatFinishedDuration(waitTimeSeconds);
        }
        return undefined;
    });

    /** Build duration formatted for display */
    buildDuration = computed(() => {
        const job = this.buildJob();
        if (!job) return undefined;
        // For finished jobs, compute from start/completion dates
        if (job.buildStartDate && job.buildCompletionDate) {
            // Clamp to 0 to handle potential clock skew or inconsistent timestamps
            const durationSeconds = Math.max(0, dayjs(job.buildCompletionDate).diff(dayjs(job.buildStartDate), 'milliseconds') / 1000);
            return this.formatFinishedDuration(durationSeconds);
        }
        // For running jobs, use jobTimingInfo.buildDuration (updated by interval)
        if (job.jobTimingInfo?.buildDuration !== undefined) {
            return this.formatLiveDuration(Math.max(0, job.jobTimingInfo.buildDuration));
        }
        if (job.buildDuration) {
            // If buildDuration is already a string, return it; otherwise format it
            if (typeof job.buildDuration === 'string') {
                return job.buildDuration;
            }
            return this.formatFinishedDuration(job.buildDuration);
        }
        return undefined;
    });

    /**
     * Formats duration for finished builds:
     * - If >= 60s: show as "Xm Ys" (e.g., "1m 3s")
     * - If < 60s: show with one decimal (e.g., "45.3s")
     */
    private formatFinishedDuration(seconds: number): string {
        if (seconds >= 60) {
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = Math.round(seconds % 60);
            return `${minutes}m ${remainingSeconds}s`;
        }
        return `${seconds.toFixed(1)}s`;
    }

    /**
     * Formats duration for live/running builds (whole seconds only)
     */
    private formatLiveDuration(seconds: number): string {
        if (seconds >= 60) {
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = Math.floor(seconds % 60);
            return `${minutes}m ${remainingSeconds}s`;
        }
        return `${Math.floor(seconds)}s`;
    }

    /** Get the status of the job */
    jobStatus = computed(() => {
        const job = this.buildJob();
        if (!job) return undefined;
        if (job.status) return job.status;
        // For BuildJobQueueItem, check if it has a buildStartDate (running) or not (queued)
        const buildStartDate = job.buildStartDate || job.jobTimingInfo?.buildStartDate;
        if (buildStartDate) return 'BUILDING';
        return 'QUEUED';
    });

    ngOnInit() {
        const courseIdParam = this.route.snapshot.paramMap.get('courseId');
        this.courseId = courseIdParam ? Number(courseIdParam) : 0;
        // Guard against NaN from invalid route params
        if (!Number.isFinite(this.courseId)) {
            this.notFound.set(true);
            this.isLoading.set(false);
            return;
        }
        this.isAdministrationView.set(this.courseId === 0);
        this.buildJobId = this.route.snapshot.paramMap.get('jobId') || '';

        if (!this.buildJobId) {
            this.notFound.set(true);
            this.isLoading.set(false);
            return;
        }

        this.loadBuildJob();
        this.initWebsocketSubscription();
        this.loadBuildAgents();

        // Update running build duration every second
        this.durationInterval = setInterval(() => {
            const job = this.buildJob();
            if (job?.jobTimingInfo?.buildStartDate && !this.isFinished()) {
                const start = dayjs(job.jobTimingInfo.buildStartDate);
                const now = dayjs();
                const updatedTimingInfo = Object.assign({}, job.jobTimingInfo, { buildDuration: now.diff(start, 'seconds') });
                const updatedJob = Object.assign({}, job, { jobTimingInfo: updatedTimingInfo });
                this.buildJob.set(updatedJob);
            }
        }, 1000);
    }

    ngOnDestroy() {
        this.wsSubscription?.unsubscribe();
        if (this.durationInterval) {
            clearInterval(this.durationInterval);
        }
    }

    private loadBuildJob() {
        this.isLoading.set(true);
        const observable = this.courseId
            ? this.buildQueueService.getBuildJobByIdForCourse(this.courseId, this.buildJobId)
            : this.buildQueueService.getBuildJobById(this.buildJobId);

        observable.subscribe({
            next: (job: any) => {
                this.buildJob.set(job);
                this.isLoading.set(false);
                // Auto-load logs for finished jobs
                if (this.isFinished()) {
                    this.loadBuildLogs();
                }
            },
            error: (err: HttpErrorResponse) => {
                if (err.status === 404) {
                    this.notFound.set(true);
                } else {
                    onError(this.alertService, err);
                }
                this.isLoading.set(false);
            },
        });
    }

    private initWebsocketSubscription() {
        const topic = this.courseId ? `/topic/courses/${this.courseId}/build-job/${this.buildJobId}` : `/topic/admin/build-job/${this.buildJobId}`;

        this.wsSubscription = this.websocketService.subscribe<BuildJob>(topic).subscribe((update: BuildJob) => {
            const wasFinished = this.isFinished();
            this.buildJob.set(update);
            // When job transitions to finished, auto-load logs
            if (!wasFinished && this.isFinished()) {
                this.loadBuildLogs();
            }
        });
    }

    private loadBuildAgents() {
        this.buildAgentsService.getBuildAgentSummary().subscribe({
            next: (agents) => this.buildAgents.set(agents),
        });
    }

    loadBuildLogs() {
        if (this.isLoadingLogs() || !this.buildJobId) return;
        this.isLoadingLogs.set(true);
        this.buildQueueService.getBuildJobLogs(this.buildJobId).subscribe({
            next: (logs: string) => {
                this.buildLogs.set(logs);
                this.isLoadingLogs.set(false);
            },
            error: (err: HttpErrorResponse) => {
                onError(this.alertService, err, false);
                this.isLoadingLogs.set(false);
            },
        });
    }

    downloadBuildLogs() {
        if (this.buildJobId && this.buildLogs()) {
            const blob = new Blob([this.buildLogs()], { type: 'text/plain' });
            try {
                downloadFile(blob, `${this.buildJobId}.log`);
            } catch (error) {
                // eslint-disable-next-line no-undef
                console.error('Failed to download build logs:', error);
                this.alertService.error('artemisApp.buildQueue.logs.downloadError');
            }
        }
    }

    /** Helper to get a display-friendly name for the build agent */
    getBuildAgentName(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        // First try to get from buildAgent object (for running jobs)
        if (job.buildAgent?.displayName || job.buildAgent?.name) {
            return job.buildAgent.displayName || job.buildAgent.name;
        }
        // For finished jobs, try to resolve address to displayName using online agents
        if (job.buildAgentAddress) {
            const agentInfo = getAgentInfoByAddress(job.buildAgentAddress, this.addressToAgentInfoMap());
            return agentInfo?.displayName ?? undefined;
        }
        return undefined;
    }

    /** Helper to get the build agent name for linking */
    getBuildAgentLinkName(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        // First try to get from buildAgent object (for running jobs)
        if (job.buildAgent?.name) {
            return job.buildAgent.name;
        }
        // For finished jobs, try to resolve address to short name using online agents
        if (job.buildAgentAddress) {
            const agentInfo = getAgentInfoByAddress(job.buildAgentAddress, this.addressToAgentInfoMap());
            return agentInfo?.name ?? undefined;
        }
        return undefined;
    }

    /** Get participation ID */
    getParticipationId(): number | undefined {
        return this.buildJob()?.participationId;
    }

    /** Get course ID from job */
    getJobCourseId(): number | undefined {
        return this.buildJob()?.courseId;
    }

    /** Get exercise ID */
    getExerciseId(): number | undefined {
        return this.buildJob()?.exerciseId;
    }

    /** Get the commit hash */
    getCommitHash(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.commitHash || job.buildConfig?.commitHashToBuild;
    }

    /** Get triggered by push to */
    getTriggeredByPushTo(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.triggeredByPushTo || job.repositoryInfo?.triggeredByPushTo;
    }

    /** Get repository name */
    getRepositoryName(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.repositoryName || job.repositoryInfo?.repositoryName;
    }

    /** Get repository type */
    getRepositoryType(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.repositoryType || job.repositoryInfo?.repositoryType;
    }

    /** Get the name of the job */
    getJobName(): string | undefined {
        return this.buildJob()?.name;
    }

    /** Get the ID of the job */
    getJobId(): string | undefined {
        return this.buildJob()?.id;
    }

    /** Get submission date */
    getSubmissionDate(): any {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.buildSubmissionDate || job.jobTimingInfo?.submissionDate;
    }

    /** Get build start date */
    getBuildStartDate(): any {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.buildStartDate || job.jobTimingInfo?.buildStartDate;
    }

    /** Get build completion date */
    getBuildCompletionDate(): any {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.buildCompletionDate || job.jobTimingInfo?.buildCompletionDate;
    }

    /** Get priority */
    getPriority(): number | undefined {
        return this.buildJob()?.priority;
    }

    /** Get retry count */
    getRetryCount(): number | undefined {
        return this.buildJob()?.retryCount;
    }

    /** Get submission result (for finished successful jobs) */
    getSubmissionResult(): any {
        return this.buildJob()?.submissionResult;
    }
}
