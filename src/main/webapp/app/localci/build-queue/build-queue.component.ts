import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BuildJob, FinishedBuildJob } from 'app/entities/build-job.model';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faFilter, faSort, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { take } from 'rxjs/operators';
import { TriggeredByPushTo } from 'app/entities/repository-info.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { NgbModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { HttpParams } from '@angular/common/http';
import { LocalStorageService } from 'ngx-webstorage';
import { Observable, OperatorFunction, Subject, merge } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';

export class FinishedBuildJobFilter {
    status?: string = undefined;
    buildAgentAddress?: string = undefined;
    buildStartDateFilterFrom?: dayjs.Dayjs = undefined;
    buildStartDateFilterTo?: dayjs.Dayjs = undefined;
    buildDurationFilterLowerBound?: number = undefined;
    buildDurationFilterUpperBound?: number = undefined;
    searchTerm?: string = undefined;

    /**
     * Adds the http param options
     * @param options request options
     */
    addHttpParams(options: HttpParams): HttpParams {
        if (this.status && this.status !== '') {
            options = options.append('buildStatus', this.status);
        }
        if (this.buildAgentAddress && this.buildAgentAddress !== '') {
            options = options.append('buildAgentAddress', this.buildAgentAddress);
        }
        if (this.buildStartDateFilterFrom) {
            options = options.append('startDate', this.buildStartDateFilterFrom.toISOString());
        }
        if (this.buildStartDateFilterTo) {
            options = options.append('endDate', this.buildStartDateFilterTo.toISOString());
        }
        if (this.buildDurationFilterLowerBound) {
            options = options.append('buildDurationLower', this.buildDurationFilterLowerBound.toString());
        }
        if (this.buildDurationFilterUpperBound) {
            options = options.append('buildDurationUpper', this.buildDurationFilterUpperBound.toString());
        }
        if (this.searchTerm && this.searchTerm !== '') {
            options = options.append('searchTerm', this.searchTerm);
        }
        return options;
    }

    /**
     * Returns the number of applied filters.
     */
    get numberOfAppliedFilters(): number {
        return Object.values(this).filter((value) => value).length;
    }

    /**
     * Resets the filter.
     */
    reset() {
        this.status = undefined;
        this.buildAgentAddress = undefined;
        this.buildStartDateFilterFrom = undefined;
        this.buildStartDateFilterTo = undefined;
    }

    /**
     * Checks if the dates are valid.
     */
    get areDatesValid(): boolean {
        if (!this.buildStartDateFilterFrom || !this.buildStartDateFilterTo) {
            return true;
        }
        return dayjs(this.buildStartDateFilterFrom).isBefore(dayjs(this.buildStartDateFilterTo));
    }

    get areDurationFiltersValid(): boolean {
        if (!this.buildDurationFilterLowerBound || !this.buildDurationFilterUpperBound) {
            return true;
        }
        return this.buildDurationFilterLowerBound < this.buildDurationFilterUpperBound;
    }
}

enum BuildJobStatusFilter {
    SUCCESSFUL = 'SUCCESSFUL',
    FAILED = 'FAILED',
    ERROR = 'ERROR',
    CANCELLED = 'CANCELLED',
}

enum FishedBuildJobFilterStorageKey {
    status = 'artemis.buildQueue.finishedBuildJobFilterStatus',
    buildAgentAddress = 'artemis.buildQueue.finishedBuildJobFilterBuildAgentAddress',
    buildStartDateFilterFrom = 'artemis.buildQueue.finishedBuildJobFilterBuildStartDateFilterFrom',
    buildStartDateFilterTo = 'artemis.buildQueue.finishedBuildJobFilterBuildStartDateFilterTo',
    buildDurationFilterLowerBound = 'artemis.buildQueue.finishedBuildJobFilterBuildDurationFilterLowerBound',
    buildDurationFilterUpperBound = 'artemis.buildQueue.finishedBuildJobFilterBuildDurationFilterUpperBound',
}

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
})
export class BuildQueueComponent implements OnInit, OnDestroy {
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
    itemsPerPage = ITEMS_PER_PAGE;
    page = 1;
    predicate = 'build_completion_date';
    ascending = false;
    interval: ReturnType<typeof setInterval>;

    // Filter
    @ViewChild('addressTypeahead', { static: true }) addressTypeahead: NgbTypeahead;
    finishedBuildJobFilter = new FinishedBuildJobFilter();
    faFilter = faFilter;
    focus$ = new Subject<string>();
    click$ = new Subject<string>();
    isLoading = false;
    search = new Subject<void>();

    constructor(
        private route: ActivatedRoute,
        private websocketService: JhiWebsocketService,
        private buildQueueService: BuildQueueService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private localStorage: LocalStorageService,
    ) {}

    ngOnInit() {
        this.loadQueue();
        this.interval = setInterval(() => {
            this.updateBuildJobDuration();
        }, 1000);
        this.initWebsocketSubscription();
        this.loadFilterFromLocalStorage();
        this.loadFinishedBuildJobs();
        this.search
            .pipe(
                debounceTime(1000),
                tap(() => (this.isLoading = true)),
                switchMap(() => this.FetchFinishedBuildJobs()),
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
        clearInterval(this.interval);
    }

    /**
     * This method is used to initialize the websocket subscription for the build jobs. It subscribes to the channels for the queued and running build jobs.
     */
    initWebsocketSubscription() {
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.websocketService.subscribe(`/topic/courses/${courseId}/queued-jobs`);
                this.websocketService.subscribe(`/topic/courses/${courseId}/running-jobs`);
                this.websocketService.receive(`/topic/courses/${courseId}/queued-jobs`).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.websocketService.receive(`/topic/courses/${courseId}/running-jobs`).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
                this.courseChannels.push(`/topic/courses/${courseId}/queued-jobs`);
                this.courseChannels.push(`/topic/courses/${courseId}/running-jobs`);
            } else {
                this.websocketService.subscribe(`/topic/admin/queued-jobs`);
                this.websocketService.subscribe(`/topic/admin/running-jobs`);
                this.websocketService.receive(`/topic/admin/queued-jobs`).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.websocketService.receive(`/topic/admin/running-jobs`).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            }
        });
    }

    /**
     * This method is used to load the build jobs from the backend when the component is initialized.
     * This ensures that the table is filled with data when the page is loaded or refreshed otherwise the user needs to
     * wait until the websocket subscription receives the data.
     */
    loadQueue() {
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.getQueuedBuildJobsByCourseId(courseId).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.buildQueueService.getRunningBuildJobsByCourseId(courseId).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            } else {
                this.buildQueueService.getQueuedBuildJobs().subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.buildQueueService.getRunningBuildJobs().subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = runningBuildJobs;
                });
            }
        });
    }

    /**
     * Cancel a specific build job associated with the build job id
     * @param buildJobId    the id of the build job to cancel
     */
    cancelBuildJob(buildJobId: string) {
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.cancelBuildJobInCourse(courseId, buildJobId).subscribe();
            } else {
                this.buildQueueService.cancelBuildJob(buildJobId).subscribe();
            }
        });
    }

    /**
     * Cancel all queued build jobs
     */
    cancelAllQueuedBuildJobs() {
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.cancelAllQueuedBuildJobsInCourse(courseId).subscribe();
            } else {
                this.buildQueueService.cancelAllQueuedBuildJobs().subscribe();
            }
        });
    }

    /**
     * Cancel all running build jobs
     */
    cancelAllRunningBuildJobs() {
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.cancelAllRunningBuildJobsInCourse(courseId).subscribe();
            } else {
                this.buildQueueService.cancelAllRunningBuildJobs().subscribe();
            }
        });
    }

    /**
     * fetch the finished build jobs from the server
     */
    FetchFinishedBuildJobs() {
        this.isLoading = true;
        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        if (courseId) {
            return this.buildQueueService.getFinishedBuildJobsByCourseId(courseId, {
                page: this.page,
                pageSize: this.itemsPerPage,
                sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                sortedColumn: this.predicate,
            });
        } else {
            return this.buildQueueService.getCustomFinishedBuildJobs(
                {
                    page: this.page,
                    pageSize: this.itemsPerPage,
                    sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                    sortedColumn: this.predicate,
                },
                this.finishedBuildJobFilter,
            );
        }
    }

    /**
     * subscribe to the finished build jobs observable
     */
    loadFinishedBuildJobs() {
        this.FetchFinishedBuildJobs().subscribe({
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

    triggerLoadFinishedJobs() {
        if (!this.finishedBuildJobFilter.searchTerm || this.finishedBuildJobFilter.searchTerm.length >= 3) {
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
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.finishedBuildJobs = finishedBuildJobs;
        this.setFinishedBuildJobsDuration();
    }

    /**
     * View the build logs of a specific build job
     * @param resultId The id of the build job
     */
    viewBuildLogs(resultId: number): void {
        const url = `/api/build-log/${resultId}`;
        window.open(url, '_blank');
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
                    buildJob.buildDuration = end.diff(start, 'milliseconds') / 1000;
                }
            }
        }
    }

    /**
     * Callback function when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        if (pageNumber) {
            this.page = pageNumber;
            this.loadFinishedBuildJobs();
        }
    }

    /**
     * Callback function to refresh the finished build jobs
     */
    refresh() {
        this.loadFinishedBuildJobs();
    }

    /**
     * Update the build jobs duration
     */
    updateBuildJobDuration() {
        if (!this.runningBuildJobs) {
            return;
        }

        for (const buildJob of this.runningBuildJobs) {
            if (buildJob.jobTimingInfo && buildJob.jobTimingInfo?.buildStartDate) {
                const start = dayjs(buildJob.jobTimingInfo?.buildStartDate);
                const now = dayjs();
                buildJob.jobTimingInfo.buildDuration = now.diff(start, 'seconds');
            }
        }
        // This is necessary to update the view when the build job duration is updated
        this.runningBuildJobs = JSON.parse(JSON.stringify(this.runningBuildJobs));
    }

    /**
     * Opens the modal.
     */
    open(content: any) {
        this.modalService.open(content);
    }

    get buildJobStatusFilter() {
        return Object.values(BuildJobStatusFilter);
    }

    /**
     * Get all build agents' addresses from the finished build jobs.
     */
    get buildAgentAddresses(): string[] {
        return Array.from(new Set(this.finishedBuildJobs.map((buildJob) => buildJob.buildAgentAddress ?? '').filter((address) => address !== '')));
    }

    // Workaround for the NgbTypeahead issue: https://github.com/ng-bootstrap/ng-bootstrap/issues/2400
    clickEvents($event: Event, typeaheadInstance: NgbTypeahead) {
        if (typeaheadInstance.isPopupOpen()) {
            this.click$.next(($event.target as HTMLInputElement).value);
        }
    }

    typeaheadSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        const buildAgentAddresses = this.buildAgentAddresses;
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.click$;
        const inputFocus$ = this.focus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term === '' ? buildAgentAddresses : buildAgentAddresses.filter((v) => v.toLowerCase().indexOf(term.toLowerCase()) > -1)).slice(0, 10)),
        );
    };

    applyFilter() {
        this.loadFinishedBuildJobs();
        this.modalService.dismissAll();
    }

    loadFilterFromLocalStorage() {
        // Iterate over all keys of the filter and load the values from the local storage if they exist.
        for (const key in FishedBuildJobFilterStorageKey) {
            const value = this.localStorage.retrieve(FishedBuildJobFilterStorageKey[key]);
            if (value) {
                this.finishedBuildJobFilter[key] = key.includes('Date') ? dayjs(value) : value;
            }
        }
    }

    /**
     * Method to add or remove a status filter and store the selected status filters in the local store if required.
     */
    toggleBuildStatusFilter(value?: string) {
        if (value) {
            this.finishedBuildJobFilter.status = value;
            this.localStorage.store(FishedBuildJobFilterStorageKey.status, value);
        } else {
            this.finishedBuildJobFilter.status = undefined;
            this.localStorage.clear(FishedBuildJobFilterStorageKey.status);
        }
    }

    filterBuildAgentAddressChanged() {
        if (this.finishedBuildJobFilter.buildAgentAddress) {
            this.localStorage.store(FishedBuildJobFilterStorageKey.buildAgentAddress, this.finishedBuildJobFilter.buildAgentAddress);
        } else {
            this.localStorage.clear(FishedBuildJobFilterStorageKey.buildAgentAddress);
        }
    }

    filterDateChanged() {
        if (!this.finishedBuildJobFilter.buildStartDateFilterFrom?.isValid()) {
            this.finishedBuildJobFilter.buildStartDateFilterFrom = undefined;
            this.localStorage.clear(FishedBuildJobFilterStorageKey.buildStartDateFilterFrom);
        } else {
            this.localStorage.store(FishedBuildJobFilterStorageKey.buildStartDateFilterFrom, this.finishedBuildJobFilter.buildStartDateFilterFrom);
        }
        if (!this.finishedBuildJobFilter.buildStartDateFilterTo?.isValid()) {
            this.finishedBuildJobFilter.buildStartDateFilterTo = undefined;
            this.localStorage.clear(FishedBuildJobFilterStorageKey.buildStartDateFilterTo);
        } else {
            this.localStorage.store(FishedBuildJobFilterStorageKey.buildStartDateFilterTo, this.finishedBuildJobFilter.buildStartDateFilterTo);
        }
    }

    filterDurationChanged() {
        if (this.finishedBuildJobFilter.buildDurationFilterLowerBound) {
            this.localStorage.store(FishedBuildJobFilterStorageKey.buildDurationFilterLowerBound, this.finishedBuildJobFilter.buildDurationFilterLowerBound);
        } else {
            this.localStorage.clear(FishedBuildJobFilterStorageKey.buildDurationFilterLowerBound);
        }
        if (this.finishedBuildJobFilter.buildDurationFilterUpperBound) {
            this.localStorage.store(FishedBuildJobFilterStorageKey.buildDurationFilterUpperBound, this.finishedBuildJobFilter.buildDurationFilterUpperBound);
        } else {
            this.localStorage.clear(FishedBuildJobFilterStorageKey.buildDurationFilterUpperBound);
        }
    }
}
