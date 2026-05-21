import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { Observable, OperatorFunction, Subject, merge } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpParams } from '@angular/common/http';
import { FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
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

    // Constructor needed for when the filter is used in the Build Agent details page
    constructor(buildAgentAddress?: string) {
        this.buildAgentAddress = buildAgentAddress;
    }

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

export enum FinishedBuildJobFilterKey {
    status = 'artemis.buildQueue.finishedBuildJobFilterStatus',
    buildAgentAddress = 'artemis.buildQueue.finishedBuildJobFilterBuildAgentAddress',
    buildSubmissionDateFilterFrom = 'artemis.buildQueue.finishedBuildJobFilterBuildSubmissionDateFilterFrom',
    buildSubmissionDateFilterTo = 'artemis.buildQueue.finishedBuildJobFilterBuildSubmissionDateFilterTo',
    buildDurationFilterLowerBound = 'artemis.buildQueue.finishedBuildJobFilterBuildDurationFilterLowerBound',
    buildDurationFilterUpperBound = 'artemis.buildQueue.finishedBuildJobFilterBuildDurationFilterUpperBound',
}

/**
 * Modal component for configuring filters on the finished build jobs list.
 * Supports filtering by:
 * - Build status (successful, failed, error, cancelled, etc.)
 * - Build agent address (with typeahead autocomplete)
 * - Build submission date range
 * - Build duration range
 *
 * The component validates filter combinations and prevents invalid configurations
 * (e.g., end date before start date, or lower bound greater than upper bound).
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-finished-builds-filter-modal',
    imports: [ArtemisTranslatePipe, TranslateDirective, NgbTypeahead, FormDateTimePickerComponent, FormsModule],
    templateUrl: './finished-builds-filter-modal.component.html',
    styleUrl: './finished-builds-filter-modal.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinishedBuildsFilterModalComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);

    /** The filter configuration being edited in this modal */
    finishedBuildJobFilter: FinishedBuildJobFilter;

    /** Available status values for the status filter dropdown */
    buildStatusFilterValues?: string[];

    /** Subject for typeahead focus events */
    focus$ = new Subject<string>();

    /** Subject for typeahead click events */
    click$ = new Subject<string>();

    /** List of finished build jobs used to extract unique build agent addresses */
    finishedBuildJobs: FinishedBuildJob[] = [];

    /** Whether the build agent filter should be shown and editable */
    buildAgentFilterable = false;

    /**
     * Initializes the component
     */
    ngOnInit() {
        this.buildStatusFilterValues = Object.values(BuildJobStatusFilter);
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
     * Method to add or remove a status filter and store the selected status filters in the local store if required.
     */
    toggleBuildStatusFilter(value?: string) {
        if (value) {
            this.finishedBuildJobFilter.status = value;
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterKey.status);
        } else {
            this.finishedBuildJobFilter.status = undefined;
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterKey.status);
        }
    }

    /**
     * Method to remove the build agent address filter and store the selected build agent address in the local store if required.
     */
    filterBuildAgentAddressChanged() {
        if (this.finishedBuildJobFilter.buildAgentAddress) {
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterKey.buildAgentAddress);
        } else {
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterKey.buildAgentAddress);
        }
    }

    /**
     * Method to remove the build start date filter and store the selected build start date in the local store if required.
     */
    filterDateChanged() {
        if (!this.finishedBuildJobFilter.buildSubmissionDateFilterFrom?.isValid()) {
            this.finishedBuildJobFilter.buildSubmissionDateFilterFrom = undefined;
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterKey.buildSubmissionDateFilterFrom);
        } else {
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterKey.buildSubmissionDateFilterFrom);
        }
        if (!this.finishedBuildJobFilter.buildSubmissionDateFilterTo?.isValid()) {
            this.finishedBuildJobFilter.buildSubmissionDateFilterTo = undefined;
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterKey.buildSubmissionDateFilterTo);
        } else {
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterKey.buildSubmissionDateFilterTo);
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
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterKey.buildDurationFilterLowerBound);
        } else {
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterKey.buildDurationFilterLowerBound);
        }
        if (this.finishedBuildJobFilter.buildDurationFilterUpperBound) {
            this.finishedBuildJobFilter.addFilterToFilterMap(FinishedBuildJobFilterKey.buildDurationFilterUpperBound);
        } else {
            this.finishedBuildJobFilter.removeFilterFromFilterMap(FinishedBuildJobFilterKey.buildDurationFilterUpperBound);
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
