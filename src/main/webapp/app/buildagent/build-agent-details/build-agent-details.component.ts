import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
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

@Component({
    selector: 'jhi-build-agent-details',
    templateUrl: './build-agent-details.component.html',
    styleUrl: './build-agent-details.component.scss',
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

    protected readonly TriggeredByPushTo = TriggeredByPushTo;
    buildAgent: BuildAgentInformation;
    buildJobStatistics: BuildJobStatistics = new BuildJobStatistics();
    runningBuildJobs: BuildJob[] = [];
    agentName: string;
    agentDetailsWebsocketSubscription: Subscription;
    runningJobsWebsocketSubscription: Subscription;
    runningJobsSubscription: Subscription;
    agentDetailsSubscription: Subscription;
    buildDurationInterval: ReturnType<typeof setInterval>;
    paramSub: Subscription;
    channel: string;
    readonly agentUpdatesChannel = '/topic/admin/build-agent';
    readonly runningBuildJobsChannel = '/topic/admin/running-jobs';

    finishedBuildJobs: FinishedBuildJob[] = [];

    hasMore = signal(true);

    //icons
    readonly faCircleCheck = faCircleCheck;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faTimes = faTimes;
    readonly faPauseCircle = faPauseCircle;
    readonly faPause = faPause;
    readonly faPlay = faPlay;
    readonly faSort = faSort;
    readonly faSync = faSync;

    readonly paginationConfig: PaginationConfig = {
        pageSize: ITEMS_PER_PAGE,
        initialPage: 1,
    };

    //Filter
    searchSubscription: Subscription;
    search = new Subject<void>();
    isLoading = false;
    searchTerm?: string = undefined;
    finishedBuildJobFilter: FinishedBuildJobFilter;
    faFilter = faFilter;

    totalItems = 0;
    itemsPerPage = ITEMS_PER_PAGE;
    page = 1;
    predicate = 'buildSubmissionDate';
    ascending = false;

    ngOnInit() {
        this.paramSub = this.route.queryParams.subscribe((params) => {
            this.agentName = params['agentName'];
            this.channel = this.agentUpdatesChannel + '/' + this.agentName;
            this.buildDurationInterval = setInterval(() => {
                this.runningBuildJobs = this.updateBuildJobDuration(this.runningBuildJobs);
            }, 1000); // 1 second
            this.load();
            this.initWebsocketSubscription();
            this.searchSubscription = this.search
                .pipe(
                    debounceTime(UI_RELOAD_TIME),
                    tap(() => (this.isLoading = true)),
                    switchMap(() => this.fetchFinishedBuildJobs()),
                )
                .subscribe({
                    next: (res: HttpResponse<FinishedBuildJob[]>) => {
                        this.onSuccess(res.body || [], res.headers);
                        this.isLoading = false;
                    },
                    error: (res: HttpErrorResponse) => {
                        onError(this.alertService, res);
                        this.isLoading = false;
                    },
                });
        });
    }

    /**
     * This method is used to unsubscribe from the websocket channels when the component is destroyed.
     */
    ngOnDestroy() {
        this.websocketService.unsubscribe(this.channel);
        this.websocketService.unsubscribe(this.runningBuildJobsChannel);
        this.agentDetailsWebsocketSubscription?.unsubscribe();
        this.runningJobsWebsocketSubscription?.unsubscribe();
        this.agentDetailsSubscription?.unsubscribe();
        this.runningJobsSubscription?.unsubscribe();
        clearInterval(this.buildDurationInterval);
        this.paramSub?.unsubscribe();
    }

    /**
     * This method is used to initialize the websocket subscription for the build agents. It subscribes to the channel for the build agents.
     */
    initWebsocketSubscription() {
        this.websocketService.subscribe(this.channel);
        this.agentDetailsWebsocketSubscription = this.websocketService.receive(this.channel).subscribe((buildAgent) => {
            this.updateBuildAgent(buildAgent);
        });
        this.websocketService.subscribe(this.runningBuildJobsChannel);
        this.runningJobsWebsocketSubscription = this.websocketService.receive(this.runningBuildJobsChannel).subscribe((runningBuildJobs) => {
            const filteredBuildJobs = runningBuildJobs.filter((buildJob: BuildJob) => buildJob.buildAgent?.name === this.agentName);
            if (filteredBuildJobs.length > 0) {
                this.runningBuildJobs = this.updateBuildJobDuration(filteredBuildJobs);
            } else {
                this.runningBuildJobs = [];
            }
        });
    }

    /**
     * This method is used to load the build agent details when the component is initialized. (Status and some stats, missing finishing build jobs)
     */
    load() {
        this.runningJobsSubscription = this.buildQueueService.getRunningBuildJobs(this.agentName).subscribe((runningBuildJobs) => {
            this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
        });
        this.agentDetailsSubscription = this.buildAgentsService.getBuildAgentDetails(this.agentName).subscribe((buildAgent) => {
            this.updateBuildAgent(buildAgent);
            this.finishedBuildJobFilter = new FinishedBuildJobFilter(this.buildAgent.buildAgent?.memberAddress);
            this.loadFinishedBuildJobs();
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

    cancelBuildJob(buildJobId: string) {
        this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
    }

    cancelAllBuildJobs() {
        if (this.buildAgent.buildAgent?.name) {
            this.buildQueueService.cancelAllRunningBuildJobsForAgent(this.buildAgent.buildAgent?.name).subscribe();
        }
    }

    viewBuildLogs(resultId: string): void {
        const url = `/api/programming/build-log/${resultId}`;
        window.open(url, '_blank');
    }

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
     * subscribe to the finished build jobs observable
     */
    loadFinishedBuildJobs() {
        this.fetchFinishedBuildJobs().subscribe({
            next: (res: HttpResponse<FinishedBuildJob[]>) => {
                this.onSuccess(res.body || [], res.headers);
                this.isLoading = false;
            },
            error: (res: HttpErrorResponse) => {
                onError(this.alertService, res);
                this.isLoading = false;
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
     * fetch the finished build jobs from the server by creating observable
     */
    fetchFinishedBuildJobs() {
        return this.buildQueueService.getFinishedBuildJobs(
            {
                page: this.page,
                pageSize: this.itemsPerPage,
                sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                sortedColumn: this.predicate,
                searchTerm: this.searchTerm || '',
            },
            this.finishedBuildJobFilter,
        );
    }

    /**
     * Method to trigger the loading of the finished build jobs by pushing a new value to the search observable
     */
    triggerLoadFinishedJobs() {
        if (!this.searchTerm || this.searchTerm.length >= 3) {
            this.search.next();
        }
    }

    /**
     * Callback function when the user navigates through the page results
     *
     * @param event The event containing the new page number
     */
    onPageChange(event: PageChangeEvent) {
        const newPage = event?.page;
        if (newPage) {
            this.page = newPage;
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
