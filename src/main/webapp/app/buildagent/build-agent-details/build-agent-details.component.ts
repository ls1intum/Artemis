import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';
import { Subject, Subscription, debounceTime, switchMap, tap } from 'rxjs';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faFilter, faPause, faPauseCircle, faPlay, faSort, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { BuildJob, BuildJobStatistics, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';
import { PageChangeEvent, PaginationConfig, SliceNavigatorComponent } from 'app/shared/components/slice-navigator/slice-navigator.component';

/**
 * Component that displays detailed information about a specific build agent.
 * Shows the agent's current status, running build jobs, finished build jobs with filtering,
 * and build job statistics. Supports real-time updates via WebSocket.
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-build-agent-details',
    templateUrl: './build-agent-details.component.html',
    styleUrl: './build-agent-details.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        NgxDatatableModule,
        DataTableComponent,
        ArtemisDurationFromSecondsPipe,
        ArtemisDatePipe,
        FontAwesomeModule,
        RouterModule,
        CommonModule,
        ResultComponent,
        TranslateDirective,
        BuildJobStatisticsComponent,
        HelpIconComponent,
        SortByDirective,
        SortDirective,
        DataTableComponent,
        NgxDatatableModule,
        FormsModule,
        SliceNavigatorComponent,
    ],
})
export class BuildAgentDetailsComponent implements OnInit, OnDestroy {
    private readonly websocketService = inject(WebsocketService);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly route = inject(ActivatedRoute);
    private readonly buildQueueService = inject(BuildOverviewService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);

    protected readonly TriggeredByPushTo = TriggeredByPushTo;

    /** Current build agent information including status and configuration */
    buildAgent: BuildAgentInformation;

    /** Aggregated statistics for build jobs processed by this agent */
    buildJobStatistics: BuildJobStatistics = new BuildJobStatistics();

    /** List of currently running build jobs on this agent */
    runningBuildJobs: BuildJob[] = [];

    /** Name of the build agent being viewed, extracted from route query params */
    agentName: string;

    /**
     * WebSocket subscription for agent details updates.
     * @internal Exposed for testing purposes only.
     */
    agentDetailsWebsocketSubscription?: Subscription;

    /**
     * WebSocket subscription for running jobs updates.
     * @internal Exposed for testing purposes only.
     */
    runningJobsWebsocketSubscription?: Subscription;

    /** Subscription for initial running jobs REST API load */
    runningJobsSubscription: Subscription;

    /** Subscription for initial agent details REST API load */
    agentDetailsSubscription: Subscription;

    /** Interval timer for updating running build job durations every second */
    buildDurationInterval: ReturnType<typeof setInterval>;

    /** Subscription for route query parameter changes */
    routeParamsSubscription: Subscription;

    /** WebSocket channel for receiving agent-specific updates (constructed from base topic + agent name) */
    agentDetailsWebsocketChannel: string;

    /** Base WebSocket topic for agent updates */
    readonly agentUpdatesChannel = '/topic/admin/build-agent';

    /** WebSocket topic for receiving running jobs updates across all agents */
    readonly runningBuildJobsChannel = '/topic/admin/running-jobs';

    /** List of finished build jobs for this agent with pagination */
    finishedBuildJobs: FinishedBuildJob[] = [];

    /** Signal indicating if more finished build jobs are available for pagination */
    hasMore = signal(true);

    // Font Awesome icons for the UI
    readonly faCircleCheck = faCircleCheck;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faTimes = faTimes;
    readonly faPauseCircle = faPauseCircle;
    readonly faPause = faPause;
    readonly faPlay = faPlay;
    readonly faSort = faSort;
    readonly faSync = faSync;

    /** Configuration for the pagination component */
    readonly paginationConfig: PaginationConfig = {
        pageSize: ITEMS_PER_PAGE,
        initialPage: 1,
    };

    // Search and filter configuration
    /** Subscription for debounced search input handling */
    searchSubscription: Subscription;

    /** Subject for triggering debounced search requests for finished build jobs */
    finishedJobsSearchTrigger = new Subject<void>();

    /** Flag indicating if finished build jobs are currently being loaded */
    isLoading = false;

    /** Current search term for filtering finished build jobs */
    searchTerm?: string = undefined;

    /** Filter configuration for finished build jobs */
    finishedBuildJobFilter: FinishedBuildJobFilter;
    faFilter = faFilter;

    /** Total number of finished build jobs (used for pagination) */
    totalItems = 0;

    /** Number of items to display per page */
    itemsPerPage = ITEMS_PER_PAGE;

    /** Current page number for pagination (1-indexed) */
    currentPage = 1;

    /** Column to sort finished build jobs by */
    predicate = 'buildSubmissionDate';

    /** Sort direction: false = descending, true = ascending */
    ascending = false;

    ngOnInit() {
        // Subscribe to route query params to get the agent name and initialize data loading
        this.routeParamsSubscription = this.route.queryParams.subscribe((params) => {
            this.agentName = params['agentName'];
            // Construct the WebSocket channel by combining base topic with agent name
            this.agentDetailsWebsocketChannel = this.agentUpdatesChannel + '/' + this.agentName;
            this.buildDurationInterval = setInterval(() => {
                this.runningBuildJobs = this.updateBuildJobDuration(this.runningBuildJobs);
            }, 1000); // 1 second
            this.loadAgentData();
            this.initWebsocketSubscription();
            // Set up debounced search for finished build jobs to avoid excessive API calls
            this.searchSubscription = this.finishedJobsSearchTrigger
                .pipe(
                    debounceTime(UI_RELOAD_TIME),
                    tap(() => (this.isLoading = true)),
                    switchMap(() => this.fetchFinishedBuildJobs()),
                )
                .subscribe({
                    next: (response: HttpResponse<FinishedBuildJob[]>) => {
                        this.onSuccess(response.body || [], response.headers);
                        this.isLoading = false;
                        this.changeDetectorRef.markForCheck();
                    },
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                        this.isLoading = false;
                        this.changeDetectorRef.markForCheck();
                    },
                });
        });
    }

    /**
     * This method is used to unsubscribe from the websocket channels when the component is destroyed.
     */
    ngOnDestroy() {
        this.agentDetailsWebsocketSubscription?.unsubscribe();
        this.runningJobsWebsocketSubscription?.unsubscribe();
        this.agentDetailsSubscription?.unsubscribe();
        this.runningJobsSubscription?.unsubscribe();
        clearInterval(this.buildDurationInterval);
        this.routeParamsSubscription?.unsubscribe();
    }

    /**
     * Initializes WebSocket subscriptions for real-time updates.
     * Subscribes to two channels:
     * 1. Agent-specific channel for status updates of this build agent
     * 2. Global running jobs channel, filtered to show only jobs for this agent
     */
    initWebsocketSubscription() {
        // Subscribe to agent-specific updates (status changes, configuration updates)
        this.agentDetailsWebsocketSubscription = this.websocketService
            .subscribe<BuildAgentInformation>(this.agentDetailsWebsocketChannel)
            .subscribe((buildAgent: BuildAgentInformation) => {
                this.updateBuildAgent(buildAgent);
                this.changeDetectorRef.markForCheck();
            });

        // Subscribe to all running jobs and filter to only show jobs for this agent
        this.runningJobsWebsocketSubscription = this.websocketService.subscribe<BuildJob[]>(this.runningBuildJobsChannel).subscribe((allRunningBuildJobs: BuildJob[]) => {
            // Filter to only include jobs running on this specific agent
            const agentRunningJobs = allRunningBuildJobs.filter((buildJob: BuildJob) => buildJob.buildAgent?.name === this.agentName);
            if (agentRunningJobs.length > 0) {
                this.runningBuildJobs = this.updateBuildJobDuration(agentRunningJobs);
            } else {
                this.runningBuildJobs = [];
            }
            this.changeDetectorRef.markForCheck();
        });
    }

    /**
     * Loads initial agent data from the REST API.
     * This provides immediate data display while WebSocket connections are being established.
     * Loads both running jobs and agent details in parallel for faster initial render.
     */
    loadAgentData() {
        // Load running jobs for this agent
        this.runningJobsSubscription = this.buildQueueService.getRunningBuildJobs(this.agentName).subscribe((runningBuildJobs) => {
            this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
            this.changeDetectorRef.markForCheck();
        });

        // Load agent details including status and statistics
        this.agentDetailsSubscription = this.buildAgentsService.getBuildAgentDetails(this.agentName).subscribe((buildAgent) => {
            this.updateBuildAgent(buildAgent);
            // Initialize filter with this agent's address to show only its finished jobs
            this.finishedBuildJobFilter = new FinishedBuildJobFilter(this.buildAgent.buildAgent?.memberAddress);
            this.loadFinishedBuildJobs();
            this.changeDetectorRef.markForCheck();
        });
    }

    private updateBuildAgent(buildAgent: BuildAgentInformation) {
        this.buildAgent = buildAgent;
        this.buildJobStatistics = {
            successfulBuilds: this.buildAgent.buildAgentDetails?.successfulBuilds || 0,
            failedBuilds: this.buildAgent.buildAgentDetails?.failedBuilds || 0,
            cancelledBuilds: this.buildAgent.buildAgentDetails?.cancelledBuilds || 0,
            timeOutBuilds: this.buildAgent.buildAgentDetails?.timedOutBuild || 0,
            totalBuilds: this.buildAgent.buildAgentDetails?.totalBuilds || 0,
            missingBuilds: 0,
        };
    }

    /**
     * Cancels a specific build job by its ID.
     * @param buildJobId The unique identifier of the build job to cancel
     */
    cancelBuildJob(buildJobId: string) {
        this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
    }

    /**
     * Cancels all running build jobs on this build agent.
     */
    cancelAllBuildJobs() {
        if (this.buildAgent.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(this.buildAgent.buildAgent?.name).subscribe();
        }
    }

    /**
     * Opens the build logs for a specific result in a new browser tab.
     * @param resultId The ID of the result whose build logs to view
     */
    viewBuildLogs(resultId: string): void {
        const url = `/api/programming/build-log/${resultId}`;
        window.open(url, '_blank');
    }

    /**
     * Pauses this build agent, preventing it from accepting new build jobs.
     * Shows success/error alerts based on the operation result.
     */
    pauseBuildAgent(): void {
        if (this.buildAgent.buildAgent?.name) {
            this.buildAgentsService.pauseBuildAgent(this.buildAgent.buildAgent.name).subscribe({
                next: () => {
                    this.alertService.addAlert({
                        type: AlertType.SUCCESS,
                        message: 'artemisApp.buildAgents.alerts.buildAgentPaused',
                    });
                },
                error: () => {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: 'artemisApp.buildAgents.alerts.buildAgentPauseFailed',
                    });
                },
            });
        } else {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
            });
        }
    }

    /**
     * Resumes this build agent, allowing it to accept new build jobs again.
     * Shows success/error alerts based on the operation result.
     */
    resumeBuildAgent(): void {
        if (this.buildAgent.buildAgent?.name) {
            this.buildAgentsService.resumeBuildAgent(this.buildAgent.buildAgent.name).subscribe({
                next: () => {
                    this.alertService.addAlert({
                        type: AlertType.SUCCESS,
                        message: 'artemisApp.buildAgents.alerts.buildAgentResumed',
                    });
                },
                error: () => {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: 'artemisApp.buildAgents.alerts.buildAgentResumeFailed',
                    });
                },
            });
        } else {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName',
            });
        }
    }

    /**
     * Opens the filter modal for configuring finished build job filters.
     * When the modal closes with a result, applies the new filter and reloads data.
     */
    openFilterModal() {
        const modalRef = this.modalService.open(FinishedBuildsFilterModalComponent as Component);
        modalRef.componentInstance.finishedBuildJobFilter = this.finishedBuildJobFilter;
        modalRef.componentInstance.buildAgentAddress = this.buildAgent.buildAgent?.memberAddress;
        modalRef.componentInstance.finishedBuildJobs = this.finishedBuildJobs;
        modalRef.result
            .then((result: FinishedBuildJobFilter) => {
                this.finishedBuildJobFilter = result;
                this.loadFinishedBuildJobs();
            })
            .catch(() => {});
    }

    /**
     * Fetches finished build jobs from the server and updates the view.
     * Called on initial load and when pagination/filters change.
     */
    loadFinishedBuildJobs() {
        this.fetchFinishedBuildJobs().subscribe({
            next: (response: HttpResponse<FinishedBuildJob[]>) => {
                this.onSuccess(response.body || [], response.headers);
                this.isLoading = false;
                this.changeDetectorRef.markForCheck();
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isLoading = false;
                this.changeDetectorRef.markForCheck();
            },
        });
    }

    /**
     * Callback function when the finished build jobs are successfully loaded
     * @param finishedBuildJobs The list of finished build jobs
     * @param headers The headers of the response
     * @private
     */
    private onSuccess(finishedBuildJobs: FinishedBuildJob[], headers: HttpHeaders) {
        this.hasMore.set(headers.get('x-has-next') === 'true');
        this.finishedBuildJobs = finishedBuildJobs;
        this.setFinishedBuildJobsDuration();
    }

    /**
     * Set the duration of the finished build jobs
     */
    setFinishedBuildJobsDuration() {
        if (this.finishedBuildJobs) {
            for (const buildJob of this.finishedBuildJobs) {
                if (buildJob.buildStartDate && buildJob.buildCompletionDate) {
                    const start = dayjs(buildJob.buildStartDate);
                    const end = dayjs(buildJob.buildCompletionDate);
                    buildJob.buildDuration = (end.diff(start, 'milliseconds') / 1000).toFixed(3) + 's';
                }
            }
        }
    }

    /**
     * Creates an observable for fetching finished build jobs with current pagination and filter settings.
     * @returns Observable that emits the HTTP response containing finished build jobs
     */
    fetchFinishedBuildJobs() {
        return this.buildQueueService.getFinishedBuildJobs(
            {
                page: this.currentPage,
                pageSize: this.itemsPerPage,
                sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                sortedColumn: this.predicate,
                searchTerm: this.searchTerm || '',
            },
            this.finishedBuildJobFilter,
        );
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
     * Callback function when the user navigates through the page results
     *
     * @param event The event containing the new page number
     */
    onPageChange(event: PageChangeEvent) {
        const newPageNumber = event?.page;
        if (newPageNumber) {
            this.currentPage = newPageNumber;
            this.loadFinishedBuildJobs();
        }
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
}
