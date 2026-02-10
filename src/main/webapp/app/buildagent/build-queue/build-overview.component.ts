import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BuildJob, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { faFilter, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import dayjs from 'dayjs/esm';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { downloadFile } from 'app/shared/util/download.util';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { Subject, Subscription } from 'rxjs';
import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { PageChangeEvent, PaginationConfig, SliceNavigatorComponent } from 'app/shared/components/slice-navigator/slice-navigator.component';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';
import { BuildAgentInformation, BuildAgentStatus } from 'app/buildagent/shared/entities/build-agent-information.model';
import { RunningJobsTableComponent } from './tables/running-jobs-table/running-jobs-table.component';
import { QueuedJobsTableComponent } from './tables/queued-jobs-table/queued-jobs-table.component';
import { FinishedJobsTableComponent } from './tables/finished-jobs-table/finished-jobs-table.component';

/**
 * Component that provides an overview of the build queue system.
 * Displays three sections: queued build jobs, running build jobs, and finished build jobs.
 * Supports real-time updates via WebSocket for queue and running jobs.
 * Provides filtering, sorting, and pagination for finished build jobs.
 *
 * Can operate in two modes:
 * - Admin mode: Shows all build jobs across all courses (when courseId is 0)
 * - Course mode: Shows only build jobs for a specific course (when courseId > 0)
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-build-overview',
    templateUrl: './build-overview.component.html',
    styleUrl: './build-overview.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        HelpIconComponent,
        FaIconComponent,
        NgClass,
        FormsModule,
        BuildJobStatisticsComponent,
        SliceNavigatorComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        RunningJobsTableComponent,
        QueuedJobsTableComponent,
        FinishedJobsTableComponent,
    ],
})
export class BuildOverviewComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private websocketService = inject(WebsocketService);
    private buildQueueService = inject(BuildOverviewService);
    private buildAgentsService = inject(BuildAgentsService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    /** Reference to the statistics component for real-time updates */
    @ViewChild('statisticsComponent') statisticsComponent?: BuildJobStatisticsComponent;

    /** List of all build agents for capacity calculation */
    buildAgents = signal<BuildAgentInformation[]>([]);

    /** Computed signal for total build capacity across all active agents */
    buildCapacity = computed(() =>
        this.buildAgents()
            .filter((agent) => agent.status !== BuildAgentStatus.PAUSED && agent.status !== BuildAgentStatus.SELF_PAUSED)
            .reduce((total, agent) => total + (agent.maxNumberOfConcurrentBuildJobs || 0), 0),
    );

    /** Computed signal for current number of running builds */
    currentBuilds = computed(() => this.buildAgents().reduce((total, agent) => total + (agent.numberOfCurrentBuildJobs || 0), 0));

    /** Computed signal for number of online build agents */
    onlineAgents = computed(() => this.buildAgents().length);

    /** List of build jobs waiting in the queue to be processed */
    queuedBuildJobs = signal<BuildJob[]>([]);

    /** List of build jobs currently being executed by build agents */
    runningBuildJobs = signal<BuildJob[]>([]);

    /** List of completed build jobs with pagination support */
    finishedBuildJobs = signal<FinishedBuildJob[]>([]);

    /** Active WebSocket subscriptions for cleanup on destroy */
    websocketSubscriptions: Subscription[] = [];

    // Font Awesome icons for the UI
    readonly faSync = faSync;
    readonly faFilter = faFilter;
    readonly faTimes = faTimes;

    /** Signal indicating if more finished build jobs are available for pagination */
    hasMore = signal(true);

    /** Number of items to display per page */
    itemsPerPage = ITEMS_PER_PAGE;

    /** Current page number for pagination (1-indexed) */
    currentPage = 1;

    /** Column to sort finished build jobs by */
    predicate = 'buildSubmissionDate';

    /** Sort direction: false = descending, true = ascending */
    ascending = false;

    /** Interval timer for updating running build job durations every second */
    buildDurationInterval: ReturnType<typeof setInterval>;

    /** Subscription for debounced search input handling */
    searchSubscription: Subscription;

    /** Subject for triggering debounced search requests for finished build jobs */
    finishedJobsSearchTrigger = new Subject<void>();

    /** Flag indicating if finished build jobs are currently being loaded */
    isLoading = signal(false);

    /** Signal indicating whether the component is in administration view */
    isAdministrationView = signal(false);

    /** Current search term for filtering finished build jobs */
    searchTerm?: string = undefined;

    /** Filter configuration for finished build jobs */
    finishedBuildJobFilter: FinishedBuildJobFilter = new FinishedBuildJobFilter();

    /**
     * Course ID from route params. When 0, operates in admin mode showing all courses.
     * When > 0, filters to show only build jobs for that specific course.
     */
    courseId = 0;

    /** Configuration for the pagination component */
    paginationConfig: PaginationConfig = {
        pageSize: ITEMS_PER_PAGE,
        initialPage: 1,
    };

    /** ID of the build job whose logs are currently displayed in the modal */
    displayedBuildJobId?: string;

    /** Raw build log content as a string for display and download */
    rawBuildLogsString: string = '';

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        // NOTE: in the server administration, courseId will be parsed as 0, while in course management, it should be a positive integer
        this.isAdministrationView.set(this.courseId === 0);
        this.loadQueue();
        // Only load build agents in admin view - they are not visible in course management view
        if (this.isAdministrationView()) {
            this.loadBuildAgents();
        }
        this.buildDurationInterval = setInterval(() => {
            this.runningBuildJobs.set(this.updateBuildJobDuration(this.runningBuildJobs()));
            // Also trigger update for queued jobs to refresh waiting times
            this.queuedBuildJobs.set([...this.queuedBuildJobs()]);
        }, 1000); // 1 second
        this.loadFinishedBuildJobs();
        this.initWebsocketSubscription();
        // Set up debounced search for finished build jobs to avoid excessive API calls
        this.searchSubscription = this.finishedJobsSearchTrigger
            .pipe(
                debounceTime(UI_RELOAD_TIME),
                tap(() => this.isLoading.set(true)),
                switchMap(() => this.fetchFinishedBuildJobs()),
            )
            .subscribe({
                next: (response: HttpResponse<FinishedBuildJob[]>) => {
                    this.onSuccess(response.body || [], response.headers);
                    this.isLoading.set(false);
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                    this.isLoading.set(false);
                },
            });
    }

    /**
     * This method is used to unsubscribe from the websocket channels when the component is destroyed.
     */
    ngOnDestroy() {
        this.websocketSubscriptions.forEach((subscription) => subscription.unsubscribe());
        this.websocketSubscriptions = [];
        clearInterval(this.buildDurationInterval);
        if (this.searchSubscription) {
            this.searchSubscription.unsubscribe();
        }
    }

    /**
     * Initializes WebSocket subscriptions for real-time build job updates.
     * Subscribes to channels based on context:
     * - Course mode: course-specific queued, running, and finished job channels
     * - Admin mode: global admin channels for all courses
     */
    initWebsocketSubscription() {
        // Clean up any existing subscriptions before creating new ones
        this.websocketSubscriptions.forEach((subscription) => subscription.unsubscribe());
        this.websocketSubscriptions = [];

        if (this.courseId) {
            // Course-specific mode: subscribe to course-scoped channels
            const queuedJobsTopic = `/topic/courses/${this.courseId}/queued-jobs`;
            const runningJobsTopic = `/topic/courses/${this.courseId}/running-jobs`;
            const finishedJobsTopic = `/topic/courses/${this.courseId}/finished-jobs`;
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<BuildJob[]>(queuedJobsTopic).subscribe((queuedBuildJobs: BuildJob[]) => {
                    this.queuedBuildJobs.set(queuedBuildJobs);
                }),
            );
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<BuildJob[]>(runningJobsTopic).subscribe((runningBuildJobs: BuildJob[]) => {
                    this.runningBuildJobs.set(this.updateBuildJobDuration(runningBuildJobs));
                }),
            );
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<FinishedBuildJob>(finishedJobsTopic).subscribe((finishedBuildJob: FinishedBuildJob) => {
                    this.handleFinishedBuildJobUpdate(finishedBuildJob);
                }),
            );
        } else {
            // Admin mode: subscribe to global admin channels for all courses
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<BuildJob[]>(`/topic/admin/queued-jobs`).subscribe((queuedBuildJobs: BuildJob[]) => {
                    this.queuedBuildJobs.set(queuedBuildJobs);
                }),
            );
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<BuildJob[]>(`/topic/admin/running-jobs`).subscribe((runningBuildJobs: BuildJob[]) => {
                    this.runningBuildJobs.set(this.updateBuildJobDuration(runningBuildJobs));
                }),
            );
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<FinishedBuildJob>(`/topic/admin/finished-jobs`).subscribe((finishedBuildJob: FinishedBuildJob) => {
                    this.handleFinishedBuildJobUpdate(finishedBuildJob);
                }),
            );
            // Subscribe to build agents updates for capacity information (admin view only)
            this.websocketSubscriptions.push(
                this.websocketService.subscribe<BuildAgentInformation[]>(`/topic/admin/build-agents`).subscribe((agents: BuildAgentInformation[]) => {
                    this.buildAgents.set(agents);
                }),
            );
        }
    }

    /**
     * Handles a finished build job update received via WebSocket.
     * Updates statistics and optionally adds the job to the list if no filters are applied.
     *
     * @param finishedBuildJob the finished build job received via WebSocket
     */
    private handleFinishedBuildJobUpdate(finishedBuildJob: FinishedBuildJob) {
        // Always update statistics regardless of filters or pagination
        this.statisticsComponent?.incrementStatisticsByStatus(finishedBuildJob.status);

        // Only update the list if no filters are applied
        if (this.hasActiveFilters()) {
            return;
        }

        // Only update if we're on the first page (sorted by submission date descending)
        if (this.currentPage !== 1) {
            return;
        }

        // Calculate the duration for the new job
        const jobWithDuration = this.calculateFinishedBuildJobDuration(finishedBuildJob);

        // Add the new job at the beginning and limit to page size
        const currentJobs = this.finishedBuildJobs();
        const updatedJobs = [jobWithDuration, ...currentJobs.filter((job) => job.id !== finishedBuildJob.id)];

        // Maintain the page size by removing excess items
        if (updatedJobs.length > this.itemsPerPage) {
            updatedJobs.splice(this.itemsPerPage);
        }

        this.finishedBuildJobs.set(updatedJobs);
    }

    /**
     * Checks if any filters are currently applied to the finished build jobs list.
     * @returns true if search term or any filter is applied
     */
    private hasActiveFilters(): boolean {
        return !!(this.searchTerm && this.searchTerm.length > 0) || (this.finishedBuildJobFilter?.numberOfAppliedFilters ?? 0) > 0;
    }

    /**
     * Calculate the duration for a single finished build job
     * @param buildJob the finished build job
     * @returns the job with calculated duration
     */
    private calculateFinishedBuildJobDuration(buildJob: FinishedBuildJob): FinishedBuildJob {
        if (buildJob.buildStartDate && buildJob.buildCompletionDate) {
            const start = dayjs(buildJob.buildStartDate);
            const end = dayjs(buildJob.buildCompletionDate);
            const durationSeconds = end.diff(start, 'milliseconds') / 1000;
            return { ...buildJob, buildDuration: this.formatFinishedDuration(durationSeconds) };
        }
        return buildJob;
    }

    /**
     * Formats duration for finished builds:
     * - If >= 60s: show as "Xm Ys" (e.g., "1m 3s")
     * - If < 60s: show with one decimal (e.g., "45.3s")
     */
    formatFinishedDuration(seconds: number): string {
        if (seconds >= 60) {
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = Math.round(seconds % 60);
            return `${minutes}m ${remainingSeconds}s`;
        }
        return `${seconds.toFixed(1)}s`;
    }

    /**
     * Loads the list of build agents to display capacity information.
     */
    loadBuildAgents() {
        this.buildAgentsService.getBuildAgentSummary().subscribe((agents) => {
            this.buildAgents.set(agents);
        });
    }

    /**
     * Loads initial queue data from the REST API.
     * This provides immediate data display while WebSocket connections are being established,
     * ensuring the table shows data immediately on page load/refresh.
     */
    loadQueue() {
        if (this.courseId) {
            // Course mode: fetch only jobs for this specific course
            this.buildQueueService.getQueuedBuildJobsByCourseId(this.courseId).subscribe((queuedBuildJobs) => {
                this.queuedBuildJobs.set(queuedBuildJobs);
            });
            this.buildQueueService.getRunningBuildJobsByCourseId(this.courseId).subscribe((runningBuildJobs) => {
                this.runningBuildJobs.set(this.updateBuildJobDuration(runningBuildJobs));
            });
        } else {
            // Admin mode: fetch all jobs across all courses
            this.buildQueueService.getQueuedBuildJobs().subscribe((queuedBuildJobs) => {
                this.queuedBuildJobs.set(queuedBuildJobs);
            });
            this.buildQueueService.getRunningBuildJobs().subscribe((runningBuildJobs) => {
                this.runningBuildJobs.set(this.updateBuildJobDuration(runningBuildJobs));
            });
        }
    }

    /**
     * Cancel a specific build job associated with the build job id
     * @param buildJobId    the id of the build job to cancel
     */
    cancelBuildJob(buildJobId: string) {
        if (this.courseId) {
            this.buildQueueService.cancelBuildJobInCourse(this.courseId, buildJobId).subscribe();
        } else {
            this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
        }
    }

    /**
     * Cancel all queued build jobs
     */
    cancelAllQueuedBuildJobs() {
        if (this.courseId) {
            this.buildQueueService.cancelAllQueuedBuildJobsInCourse(this.courseId).subscribe();
        } else {
            this.buildQueueService.cancelAllQueuedBuildJobs().subscribe();
        }
    }

    /**
     * Cancel all running build jobs
     */
    cancelAllRunningBuildJobs() {
        if (this.courseId) {
            this.buildQueueService.cancelAllRunningBuildJobsInCourse(this.courseId).subscribe();
        } else {
            this.buildQueueService.cancelAllRunningBuildJobs().subscribe();
        }
    }

    /**
     * Creates an observable for fetching finished build jobs with current pagination and filter settings.
     * Automatically uses the appropriate API endpoint based on course/admin mode.
     * @returns Observable that emits the HTTP response containing finished build jobs
     */
    fetchFinishedBuildJobs() {
        const paginationOptions = {
            page: this.currentPage,
            pageSize: this.itemsPerPage,
            sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
            sortedColumn: this.predicate,
            searchTerm: this.searchTerm || '',
        };

        if (this.courseId) {
            // Course mode: fetch finished jobs for this specific course
            return this.buildQueueService.getFinishedBuildJobsByCourseId(this.courseId, paginationOptions, this.finishedBuildJobFilter);
        } else {
            // Admin mode: fetch all finished jobs across all courses
            return this.buildQueueService.getFinishedBuildJobs(paginationOptions, this.finishedBuildJobFilter);
        }
    }

    /**
     * Fetches finished build jobs from the server and updates the view.
     * Called on initial load and when pagination/filters change.
     */
    loadFinishedBuildJobs() {
        this.fetchFinishedBuildJobs().subscribe({
            next: (response: HttpResponse<FinishedBuildJob[]>) => {
                this.onSuccess(response.body || [], response.headers);
                this.isLoading.set(false);
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isLoading.set(false);
            },
        });
    }

    /**
     * Triggers a debounced reload of finished build jobs.
     * Only triggers if search term is empty or has at least 3 characters (to avoid excessive API calls).
     */
    triggerLoadFinishedJobs() {
        // Require minimum 3 characters for search to reduce unnecessary API calls
        if (!this.searchTerm || this.searchTerm.length >= 3) {
            this.finishedJobsSearchTrigger.next();
        }
    }

    /**
     * Callback function when the finished build jobs are successfully loaded
     * @param finishedBuildJobs The list of finished build jobs
     * @param headers The headers of the response
     * @private
     */
    private onSuccess(finishedBuildJobs: FinishedBuildJob[], headers: HttpHeaders) {
        this.hasMore.set(headers.get('x-has-next') === 'true');
        this.finishedBuildJobs.set(this.calculateFinishedBuildJobsDuration(finishedBuildJobs));
    }

    /**
     * View the build logs of a specific build job
     * @param modal The modal to open
     * @param buildJobId The id of the build job
     */
    viewBuildLogs(modal: any, buildJobId: string | undefined): void {
        this.rawBuildLogsString = '';
        this.displayedBuildJobId = undefined;
        if (buildJobId) {
            this.openModal(modal, true);
            this.displayedBuildJobId = buildJobId;
            this.buildQueueService.getBuildJobLogs(buildJobId).subscribe({
                next: (buildLogs: string) => {
                    this.rawBuildLogsString = buildLogs;
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res, false);
                },
            });
        }
    }
    /**
     * Download the build logs of a specific build job
     */
    downloadBuildLogs(): void {
        if (this.displayedBuildJobId && this.rawBuildLogsString) {
            const blob = new Blob([this.rawBuildLogsString], { type: 'text/plain' });
            try {
                downloadFile(blob, `${this.displayedBuildJobId}.log`);
            } catch (error) {
                this.alertService.error('artemisApp.buildQueue.logs.downloadError');
            }
        }
    }

    /**
     * Calculate the duration of the finished build jobs
     * @param finishedBuildJobs The list of finished build jobs
     * @returns A new list with calculated build durations
     */
    private calculateFinishedBuildJobsDuration(finishedBuildJobs: FinishedBuildJob[]): FinishedBuildJob[] {
        return finishedBuildJobs.map((buildJob) => this.calculateFinishedBuildJobDuration(buildJob));
    }

    /**
     * Callback function when the user navigates through the page results
     *
     * @param event
     */
    onPageChange(event: PageChangeEvent) {
        this.currentPage = event.page;
        this.loadFinishedBuildJobs();
    }

    /**
     * Update the build jobs duration
     * @param buildJobs The list of build jobs
     * @returns The updated list of build jobs with the duration
     */
    updateBuildJobDuration(buildJobs: BuildJob[]): BuildJob[] {
        // iterate over all build jobs and calculate the duration
        return buildJobs.map((buildJob) => {
            if (buildJob.jobTimingInfo && buildJob.jobTimingInfo.buildStartDate) {
                const start = dayjs(buildJob.jobTimingInfo.buildStartDate);
                const now = dayjs();
                buildJob.jobTimingInfo.buildDuration = now.diff(start, 'seconds');
            }
            // This is necessary to update the view when the build job duration is updated
            return { ...buildJob };
        });
    }

    /**
     * Opens the modal.
     */
    openModal(modal: any, fullscreen?: boolean, size?: 'sm' | 'lg' | 'xl', scrollable = true, keyboard = true) {
        this.modalService.open(modal, { size, keyboard, scrollable, fullscreen });
    }

    openFilterModal() {
        const modalRef = this.modalService.open(FinishedBuildsFilterModalComponent as Component);
        modalRef.componentInstance.finishedBuildJobFilter = this.finishedBuildJobFilter;
        modalRef.componentInstance.buildAgentFilterable = true;
        modalRef.componentInstance.finishedBuildJobs = this.finishedBuildJobs();
        modalRef.result
            .then((result: FinishedBuildJobFilter) => {
                this.finishedBuildJobFilter = result;
                this.loadFinishedBuildJobs();
            })
            .catch(() => {});
    }

    /**
     * Scrolls to a specific section on the page.
     * @param elementId The ID of the element to scroll to
     */
    scrollToSection(elementId: string) {
        const element = document.getElementById(elementId);
        if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    /**
     * Navigate to the build job detail page
     * @param jobId The ID of the build job
     */
    navigateToJobDetail(jobId: string | undefined) {
        if (!jobId) return;
        if (this.courseId) {
            this.router.navigate(['/course-management', this.courseId, 'build-overview', jobId, 'job-details']);
        } else {
            this.router.navigate(['/admin', 'build-overview', jobId, 'job-details']);
        }
    }

    /**
     * Calculate the waiting time since submission for a queued job
     * @param submissionDate The submission date of the build job
     * @returns A formatted string showing the waiting time
     */
    calculateWaitingTime(submissionDate: dayjs.Dayjs | undefined): string {
        if (!submissionDate) {
            return '-';
        }
        const now = dayjs();
        const diffSeconds = now.diff(submissionDate, 'seconds');

        if (diffSeconds < 60) {
            return `${diffSeconds}s`;
        } else if (diffSeconds < 3600) {
            const minutes = Math.floor(diffSeconds / 60);
            const seconds = diffSeconds % 60;
            return `${minutes}m ${seconds}s`;
        } else {
            const hours = Math.floor(diffSeconds / 3600);
            const minutes = Math.floor((diffSeconds % 3600) / 60);
            return `${hours}h ${minutes}m`;
        }
    }
}
