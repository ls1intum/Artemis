import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BuildJob, FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faFilter, faSort, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { TriggeredByPushTo } from 'app/programming/shared/entities/repository-info.model';
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
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { BuildJobStatisticsComponent } from 'app/buildagent/build-job-statistics/build-job-statistics.component';
import { downloadFile } from 'app/shared/util/download.util';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { Subject, Subscription } from 'rxjs';
import { FinishedBuildJobFilter, FinishedBuildsFilterModalComponent } from 'app/buildagent/build-queue/finished-builds-filter-modal/finished-builds-filter-modal.component';
import { PageChangeEvent, PaginationConfig, SliceNavigatorComponent } from 'app/shared/components/slice-navigator/slice-navigator.component';

@Component({
    selector: 'jhi-build-overview',
    templateUrl: './build-overview.component.html',
    styleUrl: './build-overview.component.scss',
    imports: [
        TranslateDirective,
        HelpIconComponent,
        FaIconComponent,
        DataTableComponent,
        NgxDatatableModule,
        NgClass,
        RouterLink,
        FormsModule,
        SortDirective,
        SortByDirective,
        ResultComponent,
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
        BuildJobStatisticsComponent,
        SliceNavigatorComponent,
    ],
})
export class BuildOverviewComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private websocketService = inject(WebsocketService);
    private buildQueueService = inject(BuildOverviewService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    protected readonly TriggeredByPushTo = TriggeredByPushTo;

    queuedBuildJobs: BuildJob[] = [];
    runningBuildJobs: BuildJob[] = [];
    finishedBuildJobs: FinishedBuildJob[] = [];
    courseChannels: string[] = [];

    //icons
    readonly faTimes = faTimes;
    readonly faSort = faSort;
    readonly faCircleCheck = faCircleCheck;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faSync = faSync;

    totalItems = 0;
    hasMore = signal(true);
    itemsPerPage = ITEMS_PER_PAGE;
    page = 1;
    predicate = 'buildSubmissionDate';
    ascending = false;
    buildDurationInterval: ReturnType<typeof setInterval>;

    searchSubscription: Subscription;
    search = new Subject<void>();
    isLoading = false;
    searchTerm?: string = undefined;
    finishedBuildJobFilter: FinishedBuildJobFilter = new FinishedBuildJobFilter();
    faFilter = faFilter;

    courseId = 0;

    /**
     * Indicates whether the user is viewing the global Server Administration view.
     * (i.e. Server Administration > Build Overview).
     *
     * - true:  Show Course ID column (multiple courses visible)
     * - false: Hide Course ID column (single course context)
     */
    isAdministrationView = false;

    paginationConfig: PaginationConfig = {
        pageSize: ITEMS_PER_PAGE,
        initialPage: 1,
    };

    displayedBuildJobId?: string;
    rawBuildLogsString: string = '';

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        // NOTE: in the server administration, courseId will be parsed as 0, while in course management, it should be a positive integer

        this.isAdministrationView = this.courseId === 0;

        this.loadQueue();
        this.buildDurationInterval = setInterval(() => {
            this.runningBuildJobs = this.updateBuildJobDuration(this.runningBuildJobs);
        }, 1000); // 1 second
        this.loadFinishedBuildJobs();
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
    }

    /**
     * This method is used to unsubscribe from the websocket channels when the component is destroyed.
     */
    ngOnDestroy() {
        this.websocketService.unsubscribe(`/topic/admin/queued-jobs`);
        this.websocketService.unsubscribe(`/topic/admin/running-jobs`);
        this.courseChannels.forEach((channel) => {
            this.websocketService.unsubscribe(channel);
        });
        clearInterval(this.buildDurationInterval);
        if (this.searchSubscription) {
            this.searchSubscription.unsubscribe();
        }
    }

    /**
     * This method is used to initialize the websocket subscription for the build jobs. It subscribes to the channels for the queued and running build jobs.
     */
    initWebsocketSubscription() {
        if (this.courseId) {
            this.websocketService.subscribe(`/topic/courses/${this.courseId}/queued-jobs`);
            this.websocketService.subscribe(`/topic/courses/${this.courseId}/running-jobs`);
            this.websocketService.receive(`/topic/courses/${this.courseId}/queued-jobs`).subscribe((queuedBuildJobs) => {
                this.queuedBuildJobs = queuedBuildJobs;
            });
            this.websocketService.receive(`/topic/courses/${this.courseId}/running-jobs`).subscribe((runningBuildJobs) => {
                this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
            });
            this.courseChannels.push(`/topic/courses/${this.courseId}/queued-jobs`);
            this.courseChannels.push(`/topic/courses/${this.courseId}/running-jobs`);
        } else {
            this.websocketService.subscribe(`/topic/admin/queued-jobs`);
            this.websocketService.subscribe(`/topic/admin/running-jobs`);
            this.websocketService.receive(`/topic/admin/queued-jobs`).subscribe((queuedBuildJobs) => {
                this.queuedBuildJobs = queuedBuildJobs;
            });
            this.websocketService.receive(`/topic/admin/running-jobs`).subscribe((runningBuildJobs) => {
                this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
            });
        }
    }

    /**
     * This method is used to load the build jobs from the server when the component is initialized.
     * This ensures that the table is filled with data when the page is loaded or refreshed otherwise the user needs to
     * wait until the websocket subscription receives the data.
     */
    loadQueue() {
        if (this.courseId) {
            this.buildQueueService.getQueuedBuildJobsByCourseId(this.courseId).subscribe((queuedBuildJobs) => {
                this.queuedBuildJobs = queuedBuildJobs;
            });
            this.buildQueueService.getRunningBuildJobsByCourseId(this.courseId).subscribe((runningBuildJobs) => {
                this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
            });
        } else {
            this.buildQueueService.getQueuedBuildJobs().subscribe((queuedBuildJobs) => {
                this.queuedBuildJobs = queuedBuildJobs;
            });
            this.buildQueueService.getRunningBuildJobs().subscribe((runningBuildJobs) => {
                this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
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
     * fetch the finished build jobs from the server by creating observable
     */
    fetchFinishedBuildJobs() {
        if (this.courseId) {
            return this.buildQueueService.getFinishedBuildJobsByCourseId(
                this.courseId,
                {
                    page: this.page,
                    pageSize: this.itemsPerPage,
                    sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                    sortedColumn: this.predicate,
                    searchTerm: this.searchTerm || '',
                },
                this.finishedBuildJobFilter,
            );
        } else {
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
     * Method to trigger the loading of the finished build jobs by pushing a new value to the search observable
     */
    triggerLoadFinishedJobs() {
        if (!this.searchTerm || this.searchTerm.length >= 3) {
            this.search.next();
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
        this.finishedBuildJobs = finishedBuildJobs;
        this.setFinishedBuildJobsDuration();
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
     * Callback function when the user navigates through the page results
     *
     * @param event
     */
    onPageChange(event: PageChangeEvent) {
        this.page = event.page;
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
        modalRef.componentInstance.finishedBuildJobs = this.finishedBuildJobs;
        modalRef.result
            .then((result: FinishedBuildJobFilter) => {
                this.finishedBuildJobFilter = result;
                this.loadFinishedBuildJobs();
            })
            .catch(() => {});
    }
}
