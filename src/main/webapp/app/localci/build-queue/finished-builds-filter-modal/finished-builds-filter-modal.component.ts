import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { Observable, OperatorFunction, Subject, merge } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpParams } from '@angular/common/http';
import { FinishedBuildJob } from 'app/entities/programming/build-job.model';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { LocalStorageService } from 'ngx-webstorage';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FormsModule } from '@angular/forms';

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

export enum BuildJobStatusFilter {
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
    selector: 'jhi-finished-builds-filter-modal',
    imports: [ArtemisTranslatePipe, TranslateDirective, NgbTypeahead, FormDateTimePickerComponent, FormsModule],
    templateUrl: './finished-builds-filter-modal.component.html',
    styleUrl: './finished-builds-filter-modal.component.scss',
})
export class FinishedBuildsFilterModalComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);
    private localStorage = inject(LocalStorageService);

    @ViewChild('addressTypeahead', { static: true }) addressTypeahead: NgbTypeahead;
    finishedBuildJobFilter = new FinishedBuildJobFilter();
    buildStatusFilterValues?: string[];
    focus$ = new Subject<string>();
    click$ = new Subject<string>();

    finishedBuildJobs: FinishedBuildJob[] = [];

    buildAgentFilterable = false;

    /**
     * Initializes the component
     */
    ngOnInit() {
        this.buildStatusFilterValues = Object.values(BuildJobStatusFilter);
        this.loadFilterFromLocalStorage();
    }

    // Workaround for the NgbTypeahead issue: https://github.com/ng-bootstrap/ng-bootstrap/issues/2400
    clickEvents($event: Event, typeaheadInstance: NgbTypeahead) {
        if (typeaheadInstance.isPopupOpen()) {
            this.click$.next(($event.target as HTMLInputElement).value);
        }
    }

    /**
     * Get all build agents' addresses from the finished build jobs.
     */
    get buildAgentAddresses(): string[] {
        return Array.from(new Set(this.finishedBuildJobs.map((buildJob) => buildJob.buildAgentAddress ?? '').filter((address) => address !== '')));
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

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.activeModal.dismiss('cancel');
    }

    confirm() {
        this.activeModal.close(this.finishedBuildJobFilter);
    }
}
