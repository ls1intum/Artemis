import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BuildJob, FinishedBuildJob } from 'app/entities/programming/build-job.model';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faFilter, faSort, faSync, faTimes } from '@fortawesome/free-solid-svg-icons';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { debounceTime, distinctUntilChanged, map, switchMap, take, tap } from 'rxjs/operators';
import { TriggeredByPushTo } from 'app/entities/programming/repository-info.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { NgbModal, NgbPagination, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'ngx-webstorage';
import { Observable, OperatorFunction, Subject, Subscription, merge } from 'rxjs';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { BuildJobStatisticsComponent } from 'app/localci/build-queue/build-job-statistics/build-job-statistics.component';
import { downloadFile } from 'app/shared/util/download.util';

export class FinishedBuildJobFilter {
    status?: string = undefined;
    buildAgentAddress?: string = undefined;
    buildSubmissionDateFilterFrom?: dayjs.Dayjs = undefined;
    buildSubmissionDateFilterTo?: dayjs.Dayjs = undefined;
    buildDurationFilterLowerBound?: number = undefined;
    buildDurationFilterUpperBound?: number = undefined;
    numberOfAppliedFilters = 0;
    appliedFilters = new Map<string, boolean>();
    areDurationFiltersValid = true;
    areDatesValid = true;

    /**
     * Adds the http param options
     * @param options request options
     */
    addHttpParams(options: HttpParams): HttpParams {
        if (this.status) {
            options = options.append('buildStatus', this.status.toUpperCase());
        }
        if (this.buildAgentAddress) {
            options = options.append('buildAgentAddress', this.buildAgentAddress);
        }
        if (this.buildSubmissionDateFilterFrom) {
            options = options.append('startDate', this.buildSubmissionDateFilterFrom.toISOString());
        }
        if (this.buildSubmissionDateFilterTo) {
            options = options.append('endDate', this.buildSubmissionDateFilterTo.toISOString());
        }
        if (this.buildDurationFilterLowerBound) {
            options = options.append('buildDurationLower', this.buildDurationFilterLowerBound.toString());
        }
        if (this.buildDurationFilterUpperBound) {
            options = options.append('buildDurationUpper', this.buildDurationFilterUpperBound.toString());
        }

        return options;
    }

    /**
     * Method to add the filter to the filter map.
     * This is used to avoid calling functions from the template.
     * @param filterKey The key of the filter
     */
    addFilterToFilterMap(filterKey: string) {
        if (!this.appliedFilters.get(filterKey)) {
            this.appliedFilters.set(filterKey, true);
            this.numberOfAppliedFilters++;
        }
    }

    /**
     * Method to remove the filter from the filter map.
     * This is used to avoid calling functions from the template.
     * @param filterKey The key of the filter
     */
    removeFilterFromFilterMap(filterKey: string) {
        if (this.appliedFilters.get(filterKey)) {
            this.appliedFilters.delete(filterKey);
            this.numberOfAppliedFilters--;
        }
    }
}

enum BuildJobStatusFilter {
    SUCCESSFUL = 'successful',
    FAILED = 'failed',
    ERROR = 'error',
    CANCELLED = 'cancelled',
    MISSING = 'missing',
    BUILDING = 'building',
    QUEUED = 'queued',
    TIMEOUT = 'timeout',
}

export enum FinishedBuildJobFilterStorageKey {
    status = 'artemis.buildQueue.finishedBuildJobFilterStatus',
    buildAgentAddress = 'artemis.buildQueue.finishedBuildJobFilterBuildAgentAddress',
    buildSubmissionDateFilterFrom = 'artemis.buildQueue.finishedBuildJobFilterBuildSubmissionDateFilterFrom',
    buildSubmissionDateFilterTo = 'artemis.buildQueue.finishedBuildJobFilterBuildSubmissionDateFilterTo',
    buildDurationFilterLowerBound = 'artemis.buildQueue.finishedBuildJobFilterBuildDurationFilterLowerBound',
    buildDurationFilterUpperBound = 'artemis.buildQueue.finishedBuildJobFilterBuildDurationFilterUpperBound',
}

@Component({
    selector: 'jhi-build-queue',
    templateUrl: './build-queue.component.html',
    styleUrl: './build-queue.component.scss',
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
        ItemCountComponent,
        NgbPagination,
        NgbTypeahead,
        FormDateTimePickerComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
        BuildJobStatisticsComponent,
    ],
})
export class BuildQueueComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private websocketService = inject(WebsocketService);
    private buildQueueService = inject(BuildQueueService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private localStorage = inject(LocalStorageService);

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
    predicate = 'buildSubmissionDate';
    ascending = false;
    buildDurationInterval: ReturnType<typeof setInterval>;

    // Filter
    @ViewChild('addressTypeahead', { static: true }) addressTypeahead: NgbTypeahead;
    finishedBuildJobFilter = new FinishedBuildJobFilter();
    buildStatusFilterValues?: string[];
    faFilter = faFilter;
    focus$ = new Subject<string>();
    click$ = new Subject<string>();
    isLoading = false;
    search = new Subject<void>();
    searchSubscription: Subscription;
    searchTerm?: string = undefined;

    displayedBuildJobId?: string;
    rawBuildLogsString: string = '';

    ngOnInit() {
        this.buildStatusFilterValues = Object.values(BuildJobStatusFilter);
        this.loadQueue();
        this.buildDurationInterval = setInterval(() => {
            this.runningBuildJobs = this.updateBuildJobDuration(this.runningBuildJobs);
        }, 1000); // 1 second
        this.loadFilterFromLocalStorage();
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
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.websocketService.subscribe(`/topic/courses/${courseId}/queued-jobs`);
                this.websocketService.subscribe(`/topic/courses/${courseId}/running-jobs`);
                this.websocketService.receive(`/topic/courses/${courseId}/queued-jobs`).subscribe((queuedBuildJobs) => {
                    this.queuedBuildJobs = queuedBuildJobs;
                });
                this.websocketService.receive(`/topic/courses/${courseId}/running-jobs`).subscribe((runningBuildJobs) => {
                    this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
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
                    this.runningBuildJobs = this.updateBuildJobDuration(runningBuildJobs);
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
     * fetch the finished build jobs from the server by creating observable
     */
    fetchFinishedBuildJobs() {
        return this.route.paramMap.pipe(
            take(1),
            tap(() => (this.isLoading = true)),
            switchMap((params) => {
                const courseId = Number(params.get('courseId'));
                if (courseId) {
                    return this.buildQueueService.getFinishedBuildJobsByCourseId(
                        courseId,
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
            }),
        );
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
        this.totalItems = Number(headers.get('X-Total-Count'));
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
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        if (pageNumber) {
            this.page = pageNumber;
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
     * Opens the modal.
     */
    openModal(modal: any, fullscreen?: boolean, size?: 'sm' | 'lg' | 'xl', scrollable = true, keyboard = true) {
        this.modalService.open(modal, { size, keyboard, scrollable, fullscreen });
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

    /**
     * Method to build the agent addresses for the typeahead search.
     * @param text$
     */
    typeaheadSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        const buildAgentAddresses = this.buildAgentAddresses;
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.click$;
        const inputFocus$ = this.focus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term === '' ? buildAgentAddresses : buildAgentAddresses.filter((v) => v.toLowerCase().indexOf(term.toLowerCase()) > -1)).slice(0, 10)),
        );
    };

    /**
     * Method to reset the filter.
     */
    applyFilter() {
        this.loadFinishedBuildJobs();
        this.modalService.dismissAll();
    }

    /**
     * Method to load the filter values from the local storage if they exist.
     */
    loadFilterFromLocalStorage() {
        this.finishedBuildJobFilter.numberOfAppliedFilters = 0;

        // Iterate over all keys of the filter and load the values from the local storage if they exist.
        const keys = Object.keys(FinishedBuildJobFilterStorageKey) as Array<keyof typeof FinishedBuildJobFilterStorageKey>;
        for (const key of keys) {
            const value = this.localStorage.retrieve(FinishedBuildJobFilterStorageKey[key]);
            if (value) {
                this.finishedBuildJobFilter[key] = key.includes('Date') ? dayjs(value) : value;
                this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey[key]);
            }
        }
    }

    /**
     * Method to add or remove a status filter and store the selected status filters in the local store if required.
     */
    toggleBuildStatusFilter(value?: string) {
        if (value) {
            this.finishedBuildJobFilter.status = value;
            this.localStorage.store(FinishedBuildJobFilterStorageKey.status, value);
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey.status);
        } else {
            this.finishedBuildJobFilter.status = undefined;
            this.localStorage.clear(FinishedBuildJobFilterStorageKey.status);
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterStorageKey.status);
        }
    }

    /**
     * Method to remove the build agent address filter and store the selected build agent address in the local store if required.
     */
    filterBuildAgentAddressChanged() {
        if (this.finishedBuildJobFilter.buildAgentAddress) {
            this.localStorage.store(FinishedBuildJobFilterStorageKey.buildAgentAddress, this.finishedBuildJobFilter.buildAgentAddress);
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey.buildAgentAddress);
        } else {
            this.localStorage.clear(FinishedBuildJobFilterStorageKey.buildAgentAddress);
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterStorageKey.buildAgentAddress);
        }
    }

    /**
     * Method to remove the build start date filter and store the selected build start date in the local store if required.
     */
    filterDateChanged() {
        if (!this.finishedBuildJobFilter.buildSubmissionDateFilterFrom?.isValid()) {
            this.finishedBuildJobFilter.buildSubmissionDateFilterFrom = undefined;
            this.localStorage.clear(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterFrom);
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterFrom);
        } else {
            this.localStorage.store(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterFrom, this.finishedBuildJobFilter.buildSubmissionDateFilterFrom);
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterFrom);
        }
        if (!this.finishedBuildJobFilter.buildSubmissionDateFilterTo?.isValid()) {
            this.finishedBuildJobFilter.buildSubmissionDateFilterTo = undefined;
            this.localStorage.clear(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterTo);
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterTo);
        } else {
            this.localStorage.store(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterTo, this.finishedBuildJobFilter.buildSubmissionDateFilterTo);
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey.buildSubmissionDateFilterTo);
        }
        if (this.finishedBuildJobFilter.buildSubmissionDateFilterFrom && this.finishedBuildJobFilter.buildSubmissionDateFilterTo) {
            this.finishedBuildJobFilter.areDatesValid = this.finishedBuildJobFilter.buildSubmissionDateFilterFrom.isBefore(this.finishedBuildJobFilter.buildSubmissionDateFilterTo);
        } else {
            this.finishedBuildJobFilter.areDatesValid = true;
        }
    }

    /**
     * Method to remove the build duration filter and store the selected build duration in the local store if required.
     */
    filterDurationChanged() {
        if (this.finishedBuildJobFilter.buildDurationFilterLowerBound) {
            this.localStorage.store(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound, this.finishedBuildJobFilter.buildDurationFilterLowerBound);
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound);
        } else {
            this.localStorage.clear(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound);
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterStorageKey.buildDurationFilterLowerBound);
        }
        if (this.finishedBuildJobFilter.buildDurationFilterUpperBound) {
            this.localStorage.store(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound, this.finishedBuildJobFilter.buildDurationFilterUpperBound);
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound);
        } else {
            this.localStorage.clear(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound);
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterStorageKey.buildDurationFilterUpperBound);
        }
        if (this.finishedBuildJobFilter.buildDurationFilterLowerBound && this.finishedBuildJobFilter.buildDurationFilterUpperBound) {
            this.finishedBuildJobFilter.areDurationFiltersValid =
                this.finishedBuildJobFilter.buildDurationFilterLowerBound <= this.finishedBuildJobFilter.buildDurationFilterUpperBound;
        } else {
            this.finishedBuildJobFilter.areDurationFiltersValid = true;
        }
    }
}
