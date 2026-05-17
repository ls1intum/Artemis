import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';
import { EMPTY, Observable, Subject, Subscription, catchError, concatMap, debounceTime, from, switchMap, tap } from 'rxjs';
import { faBroom, faCircleCheck, faEraser, faFilter, faHardDrive, faPause, faPauseCircle, faPlay, faSync } from '@fortawesome/free-solid-svg-icons';
import { DialogService } from 'primeng/dynamicdialog';
import { ReclaimDiskDialogComponent, ReclaimDiskDialogResult } from 'app/buildagent/build-agent-details/reclaim-disk-dialog/reclaim-disk-dialog.component';
import { BytesPipe } from 'app/shared/pipes/bytes.pipe';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { BuildJob, BuildJobStatistics, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { FormsModule } from '@angular/forms';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import dayjs from 'dayjs/esm';
import { BuildAgentsService } from 'app/buildagent/build-agents.service';
import { PageChangeEvent, PaginationConfig, SliceNavigatorComponent } from 'app/shared/components/slice-navigator/slice-navigator.component';
import { RunningJobsTableComponent } from 'app/buildagent/build-queue/tables/running-jobs-table/running-jobs-table.component';
import { FinishedJobsTableComponent } from 'app/buildagent/build-queue/tables/finished-jobs-table/finished-jobs-table.component';
import { extractHost, looksLikeAddress } from 'app/buildagent/shared/build-agent-address.utils';

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
        FontAwesomeModule,
        RouterModule,
        CommonModule,
        TranslateDirective,
        ArtemisDatePipe,
        BuildJobStatisticsComponent,
        HelpIconComponent,
        FormsModule,
        SliceNavigatorComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        RunningJobsTableComponent,
        FinishedJobsTableComponent,
        BytesPipe,
    ],
})
export class BuildAgentDetailsComponent implements OnInit, OnDestroy {
    private readonly websocketService = inject(WebsocketService);
    private readonly buildAgentsService = inject(BuildAgentsService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly buildQueueService = inject(BuildOverviewService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);
    private readonly dialogService = inject(DialogService);

    /** Icons surfaced in the Maintenance card. */
    protected readonly faBroom = faBroom;
    protected readonly faEraser = faEraser;
    protected readonly faHardDrive = faHardDrive;

    /** Current build agent information including status and configuration */
    buildAgent = signal<BuildAgentInformation | undefined>(undefined);

    /** Whether the build agent was not found (offline/removed) */
    agentNotFound = signal(false);

    /** Aggregated statistics for build jobs processed by this agent */
    buildJobStatistics = signal<BuildJobStatistics>(new BuildJobStatistics());

    /** List of currently running build jobs on this agent */
    runningBuildJobs = signal<BuildJob[]>([]);

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
    finishedBuildJobs = signal<FinishedBuildJob[]>([]);

    /** Signal indicating if more finished build jobs are available for pagination */
    hasMore = signal(true);

    // Font Awesome icons for the UI
    readonly faCircleCheck = faCircleCheck;
    readonly faPauseCircle = faPauseCircle;
    readonly faPause = faPause;
    readonly faPlay = faPlay;
    readonly faSync = faSync;
    readonly faFilter = faFilter;

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
    isLoading = signal(false);

    /** Current search term for filtering finished build jobs */
    searchTerm?: string = undefined;

    /** Filter configuration for finished build jobs */
    finishedBuildJobFilter: FinishedBuildJobFilter;

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
                this.runningBuildJobs.set(this.updateBuildJobDuration(this.runningBuildJobs()));
            }, 1000); // 1 second
            this.loadAgentData();
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
            });

        // Subscribe to all running jobs and filter to only show jobs for this agent
        this.runningJobsWebsocketSubscription = this.websocketService.subscribe<BuildJob[]>(this.runningBuildJobsChannel).subscribe((allRunningBuildJobs: BuildJob[]) => {
            // Filter to only include jobs running on this specific agent
            const agentRunningJobs = allRunningBuildJobs.filter((buildJob: BuildJob) => buildJob.buildAgent?.name === this.agentName);
            if (agentRunningJobs.length > 0) {
                this.runningBuildJobs.set(this.updateBuildJobDuration(agentRunningJobs));
            } else {
                this.runningBuildJobs.set([]);
            }
        });
    }

    /**
     * Loads initial agent data from the REST API.
     * This provides immediate data display while WebSocket connections are being established.
     * Loads both running jobs and agent details in parallel for faster initial render.
     *
     * If the query param looks like an address (contains brackets and port), first tries to
     * resolve it to an agent name using the list of online agents. This handles the case where
     * the address in the URL has a different port than the current agent address (due to
     * ephemeral Hazelcast client ports that change on reconnection).
     */
    loadAgentData() {
        // Check if agentName looks like an address (e.g., [192.168.1.1]:5701 or [2001:db8::1]:5701)
        // If so, try to resolve it to the actual agent name first before loading data
        if (looksLikeAddress(this.agentName)) {
            this.resolveAddressToNameThenLoadDetails();
        } else {
            this.loadRunningJobs();
            this.loadAgentDetails();
        }
    }

    /**
     * Loads running jobs for this agent.
     * Should be called after agentName is resolved to ensure correct filtering.
     */
    private loadRunningJobs() {
        this.runningJobsSubscription?.unsubscribe();
        this.runningJobsSubscription = this.buildQueueService.getRunningBuildJobs(this.agentName).subscribe((runningBuildJobs) => {
            this.runningBuildJobs.set(this.updateBuildJobDuration(runningBuildJobs));
        });
    }

    /**
     * Tries to resolve an address to an agent name using the list of online agents,
     * then loads the agent details.
     * This handles the case where the URL contains an old address with a different port.
     */
    private resolveAddressToNameThenLoadDetails() {
        this.buildAgentsService.getBuildAgentSummary().subscribe({
            next: (agents) => {
                const urlHost = extractHost(this.agentName);
                // Try to find an online agent whose address host matches
                const matchingAgent = agents.find((agent) => {
                    const agentAddress = agent.buildAgent?.memberAddress;
                    if (agentAddress) {
                        const agentHost = extractHost(agentAddress);
                        return agentHost === urlHost;
                    }
                    return false;
                });

                if (matchingAgent?.buildAgent?.name) {
                    // Found a matching online agent - use its name instead of the address
                    this.agentName = matchingAgent.buildAgent.name;
                    this.resubscribeWebsocket();
                }
                // Now load running jobs and agent details with the resolved name
                this.loadRunningJobs();
                this.loadAgentDetails();
            },
            error: () => {
                // If we can't get the agent list, just try with the original address
                this.loadRunningJobs();
                this.loadAgentDetails();
            },
        });
    }

    /**
     * Loads agent details from the API.
     */
    private loadAgentDetails() {
        this.agentDetailsSubscription?.unsubscribe();
        this.agentDetailsSubscription = this.buildAgentsService.getBuildAgentDetails(this.agentName).subscribe({
            next: (buildAgent) => {
                this.updateBuildAgent(buildAgent);
                // If we queried by address but got a different name, update for correct WebSocket subscription
                const actualName = buildAgent.buildAgent?.name;
                if (actualName && this.agentName !== actualName) {
                    this.agentName = actualName;
                    // Re-subscribe to the correct WebSocket channel
                    this.resubscribeWebsocket();
                }
                // Initialize filter with this agent's address to show only its finished jobs
                this.finishedBuildJobFilter = new FinishedBuildJobFilter(buildAgent.buildAgent?.memberAddress);
                this.loadFinishedBuildJobs();
            },
            error: (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // Agent not found - this is expected for offline/removed agents
                    this.agentNotFound.set(true);
                } else {
                    // Other errors (server error, network issue, etc.) - show alert but don't mark as not found
                    onError(this.alertService, error);
                }
                // Use the query param directly for filtering - it's likely the address when navigating from finished jobs
                // When agent is offline, buildAgent() is empty, so use this.agentName instead
                this.finishedBuildJobFilter = new FinishedBuildJobFilter(this.agentName);
                this.loadFinishedBuildJobs();
            },
        });
    }

    /**
     * Re-subscribes to WebSocket channels with the updated agent name.
     * Called when we navigate by address but need to subscribe to name-based channels.
     */
    private resubscribeWebsocket() {
        // Unsubscribe from old channels
        this.agentDetailsWebsocketSubscription?.unsubscribe();
        this.runningJobsWebsocketSubscription?.unsubscribe();

        // Update channel and re-subscribe
        this.agentDetailsWebsocketChannel = this.agentUpdatesChannel + '/' + this.agentName;
        this.initWebsocketSubscription();
    }

    private updateBuildAgent(buildAgent: BuildAgentInformation) {
        this.buildAgent.set(buildAgent);
        // Reset not-found state when we successfully receive agent data
        this.agentNotFound.set(false);
        this.buildJobStatistics.set({
            successfulBuilds: buildAgent.buildAgentDetails?.successfulBuilds || 0,
            failedBuilds: buildAgent.buildAgentDetails?.failedBuilds || 0,
            cancelledBuilds: buildAgent.buildAgentDetails?.cancelledBuilds || 0,
            timeOutBuilds: buildAgent.buildAgentDetails?.timedOutBuild || 0,
            totalBuilds: buildAgent.buildAgentDetails?.totalBuilds || 0,
            missingBuilds: 0,
        });
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
        const agent = this.buildAgent();
        if (agent?.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(agent.buildAgent.name).subscribe();
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
        const agent = this.buildAgent();
        if (agent?.buildAgent?.name) {
            this.buildAgentsService.pauseBuildAgent(agent.buildAgent.name).subscribe({
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
        const agent = this.buildAgent();
        if (agent?.buildAgent?.name) {
            this.buildAgentsService.resumeBuildAgent(agent.buildAgent.name).subscribe({
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
     * Runs the regular age + size cache cleanup on the current agent. Non-destructive — same task as the daily
     * scheduler. Returns immediately; the agent flips through MAINTENANCE status and back via WebSocket pushes.
     */
    runCacheCleanup(): void {
        const name = this.buildAgent()?.buildAgent?.name;
        if (!name) {
            this.alertService.addAlert({ type: AlertType.WARNING, message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName' });
            return;
        }
        this.buildAgentsService.runCacheCleanup(name).subscribe({
            next: () => this.alertService.addAlert({ type: AlertType.SUCCESS, message: 'artemisApp.buildAgents.alerts.cleanupTriggered' }),
            error: () => this.alertService.addAlert({ type: AlertType.DANGER, message: 'artemisApp.buildAgents.alerts.cleanupFailed' }),
        });
    }

    /**
     * Opens the Reclaim disk dialog. On confirm with one or more options selected, fires the matching REST calls
     * concurrently. Each call returns 204 immediately; the actions themselves run serially on the build agent
     * because they all acquire the maintenance pause. Toasts give per-action feedback.
     */
    openReclaimDiskDialog(): void {
        const agent = this.buildAgent();
        const name = agent?.buildAgent?.name;
        if (!name) {
            this.alertService.addAlert({ type: AlertType.WARNING, message: 'artemisApp.buildAgents.alerts.buildAgentWithoutName' });
            return;
        }
        const details = agent?.buildAgentDetails;
        const ref = this.dialogService.open(ReclaimDiskDialogComponent, {
            header: this.translateHeader(name),
            width: '40rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: {
                agentName: name,
                pauseGracePeriodSeconds: agent?.pauseAfterConsecutiveBuildFailures ? undefined : 60, // server default
                diskTotalBytes: details?.diskTotalBytes,
                diskUsableBytes: details?.diskUsableBytes,
                mavenCacheBytes: details?.mavenCacheBytes,
                gradleCacheBytes: details?.gradleCacheBytes,
                dockerUnusedImageBytes: details?.dockerUnusedImageBytes,
                dockerUnusedImageCount: details?.dockerUnusedImageCount,
            },
        });
        ref?.onClose.subscribe((result?: ReclaimDiskDialogResult) => {
            if (!result) {
                return;
            }
            this.dispatchReclaim(name, result);
        });
    }

    private translateHeader(agentName: string): string {
        // Header rendered as a plain string by PrimeNG; the translation service is the canonical formatter
        // elsewhere in the module, but here we rely on the i18n key being interpolated by the directive when the
        // header itself is rendered. PrimeNG cannot interpolate jhiTranslate, so we pass a fallback English string;
        // the translation directive on the template body still works for everything inside the dialog.
        return `Reclaim disk on ${agentName}`;
    }

    private dispatchReclaim(name: string, result: ReclaimDiskDialogResult): void {
        // Sequence the selected actions one after another (concat, not parallel). The server-side maintenance
        // listener holds the agent's pause for the duration of each action; a second REST call that lands while
        // the first is still draining its in-flight builds would observe the agent already paused and quietly
        // skip with reason "already-paused". By chaining we ensure the operator's intent — "run all three of
        // these" — actually translates to three completed actions, with each toast appearing in the order the
        // dialog declared.
        const steps: { run: () => Observable<void>; ok: string; fail: string }[] = [];
        if (result.wipeMaven) {
            steps.push({
                run: () => this.buildAgentsService.wipeMavenCache(name),
                ok: 'artemisApp.buildAgents.alerts.mavenWipeTriggered',
                fail: 'artemisApp.buildAgents.alerts.mavenWipeFailed',
            });
        }
        if (result.wipeGradle) {
            steps.push({
                run: () => this.buildAgentsService.wipeGradleCache(name),
                ok: 'artemisApp.buildAgents.alerts.gradleWipeTriggered',
                fail: 'artemisApp.buildAgents.alerts.gradleWipeFailed',
            });
        }
        if (result.clearDocker) {
            steps.push({
                run: () => this.buildAgentsService.clearDockerImages(name),
                ok: 'artemisApp.buildAgents.alerts.dockerClearTriggered',
                fail: 'artemisApp.buildAgents.alerts.dockerClearFailed',
            });
        }
        from(steps)
            .pipe(
                concatMap((step) =>
                    step.run().pipe(
                        tap({
                            next: () => this.alertService.addAlert({ type: AlertType.SUCCESS, message: step.ok }),
                            error: () => this.alertService.addAlert({ type: AlertType.DANGER, message: step.fail }),
                        }),
                        // Swallow a step's error so the next step still runs (each step is independent; a Docker
                        // clear failure should not silently cancel the Gradle wipe the operator also asked for).
                        catchError(() => EMPTY),
                    ),
                ),
            )
            .subscribe();
    }

    /**
     * Opens the filter modal for configuring finished build job filters.
     * When the modal closes with a result, applies the new filter and reloads data.
     */
    openFilterModal() {
        const modalRef = this.modalService.open(FinishedBuildsFilterModalComponent as Component);
        modalRef.componentInstance.finishedBuildJobFilter = this.finishedBuildJobFilter;
        modalRef.componentInstance.buildAgentAddress = this.buildAgent()?.buildAgent?.memberAddress;
        modalRef.componentInstance.finishedBuildJobs = this.finishedBuildJobs();
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
                this.isLoading.set(false);
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isLoading.set(false);
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
        this.finishedBuildJobs.set(this.calculateFinishedBuildJobsDuration(finishedBuildJobs));
    }

    /**
     * Calculate the duration of the finished build jobs
     * @param finishedBuildJobs The list of finished build jobs
     * @returns A new list with calculated build durations
     */
    private calculateFinishedBuildJobsDuration(finishedBuildJobs: FinishedBuildJob[]): FinishedBuildJob[] {
        return finishedBuildJobs.map((buildJob) => {
            if (buildJob.buildStartDate && buildJob.buildCompletionDate) {
                const start = dayjs(buildJob.buildStartDate);
                const end = dayjs(buildJob.buildCompletionDate);
                return { ...buildJob, buildDuration: (end.diff(start, 'milliseconds') / 1000).toFixed(3) + 's' };
            }
            return buildJob;
        });
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

    /**
     * Navigate to the build job detail page.
     * @param jobId The ID of the build job
     */
    navigateToJobDetail(jobId: string): void {
        this.router.navigate(['/admin', 'build-overview', jobId, 'job-details']);
    }
}
