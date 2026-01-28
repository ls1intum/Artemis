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
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { NgClass } from '@angular/common';
import { ResultComponent } from 'app/exercise/result/result.component';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { downloadFile } from 'app/shared/util/download.util';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';

@Component({
    selector: 'jhi-build-job-detail',
    templateUrl: './build-job-detail.component.html',
    styleUrl: './build-job-detail.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FaIconComponent, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, NgClass, RouterLink, ResultComponent, AdminTitleBarTitleDirective],
})
export class BuildJobDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private buildQueueService = inject(BuildOverviewService);
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

    /** The build job data - either a BuildJob (queued/running) or FinishedBuildJob */
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

    /** Whether the job is finished */
    isFinished = computed(() => {
        const job = this.buildJob();
        if (!job) return false;
        // FinishedBuildJob has buildCompletionDate, BuildJob has status
        return (
            !!job.buildCompletionDate || job.status === 'SUCCESSFUL' || job.status === 'FAILED' || job.status === 'CANCELLED' || job.status === 'ERROR' || job.status === 'TIMEOUT'
        );
    });

    /** Computed queue wait time in seconds */
    queueWaitTime = computed(() => {
        const job = this.buildJob();
        if (!job) return undefined;
        const submissionDate = job.buildSubmissionDate || job.jobTimingInfo?.submissionDate;
        const buildStartDate = job.buildStartDate || job.jobTimingInfo?.buildStartDate;
        if (submissionDate && buildStartDate) {
            return dayjs(buildStartDate).diff(dayjs(submissionDate), 'seconds');
        }
        return undefined;
    });

    /** Build duration in seconds */
    buildDuration = computed(() => {
        const job = this.buildJob();
        if (!job) return undefined;
        // For finished jobs, compute from start/completion dates
        if (job.buildStartDate && job.buildCompletionDate) {
            return (dayjs(job.buildCompletionDate).diff(dayjs(job.buildStartDate), 'milliseconds') / 1000).toFixed(3) + 's';
        }
        // For running jobs, use jobTimingInfo.buildDuration (updated by interval)
        if (job.jobTimingInfo?.buildDuration) {
            return job.jobTimingInfo.buildDuration;
        }
        if (job.buildDuration) {
            return job.buildDuration;
        }
        return undefined;
    });

    /** Get the status of the job */
    jobStatus = computed(() => {
        const job = this.buildJob();
        if (!job) return undefined;
        if (job.status) return job.status;
        // For BuildJobQueueItem, check if it has a buildAgent (running) or not (queued)
        if (job.buildAgent) return 'BUILDING';
        return 'QUEUED';
    });

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId') || 0);
        this.isAdministrationView.set(this.courseId === 0);
        this.buildJobId = this.route.snapshot.paramMap.get('jobId') || '';

        if (!this.buildJobId) {
            this.notFound.set(true);
            this.isLoading.set(false);
            return;
        }

        this.loadBuildJob();
        this.initWebsocketSubscription();

        // Update running build duration every second
        this.durationInterval = setInterval(() => {
            const job = this.buildJob();
            if (job?.jobTimingInfo?.buildStartDate && !this.isFinished()) {
                const start = dayjs(job.jobTimingInfo.buildStartDate);
                const now = dayjs();
                const updatedJob = { ...job, jobTimingInfo: { ...job.jobTimingInfo, buildDuration: now.diff(start, 'seconds') } };
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
            } catch {
                this.alertService.error('artemisApp.buildQueue.logs.downloadError');
            }
        }
    }

    /** Helper to get a display-friendly name for the build agent */
    getBuildAgentName(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.buildAgentAddress || job.buildAgent?.displayName || job.buildAgent?.name;
    }

    /** Helper to get the build agent name for linking */
    getBuildAgentLinkName(): string | undefined {
        const job = this.buildJob();
        if (!job) return undefined;
        return job.buildAgentAddress || job.buildAgent?.name;
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
