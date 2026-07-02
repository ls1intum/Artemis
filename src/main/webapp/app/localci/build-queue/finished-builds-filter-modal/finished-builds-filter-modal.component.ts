import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import dayjs from 'dayjs/esm';
import { HttpParams } from '@angular/common/http';
import { FinishedBuildJob } from 'app/localci/shared/entities/build-job.model';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { InputTextModule } from 'primeng/inputtext';
import { RadioButtonModule } from 'primeng/radiobutton';

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
    imports: [ArtemisTranslatePipe, TranslateDirective, FormDateTimePickerComponent, FormsModule, ButtonModule, AutoCompleteModule, InputTextModule, RadioButtonModule],
    templateUrl: './finished-builds-filter-modal.component.html',
    styleUrl: './finished-builds-filter-modal.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinishedBuildsFilterModalComponent implements OnInit {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    /**
     * The filter configuration being edited in this modal.
     * Backed by a signal so template reads stay reactive under zoneless change detection, but exposed as a
     * getter/setter property because the template uses deep two-way bindings ([(ngModel)]="finishedBuildJobFilter.prop")
     * that a bare signal cannot back. After deep mutations the reference is rebuilt via commitFinishedBuildJobFilter().
     */
    private readonly finishedBuildJobFilterSignal = signal<FinishedBuildJobFilter>(undefined!);
    get finishedBuildJobFilter(): FinishedBuildJobFilter {
        return this.finishedBuildJobFilterSignal();
    }
    set finishedBuildJobFilter(value: FinishedBuildJobFilter) {
        this.finishedBuildJobFilterSignal.set(value);
    }

    /** Rebuilds the filter reference so signal consumers (the template) react to deep in-place mutations. */
    private commitFinishedBuildJobFilter(): void {
        this.finishedBuildJobFilterSignal.update((filter) =>
            Object.assign(new FinishedBuildJobFilter(filter.buildAgentAddress), filter, { appliedFilters: filter.appliedFilters }),
        );
    }

    /** Available status values for the status filter dropdown */
    buildStatusFilterValues?: string[];

    /** Suggestions shown in the build agent address autocomplete dropdown */
    readonly buildAgentAddressSuggestions = signal<string[]>([]);

    /** List of finished build jobs used to extract unique build agent addresses */
    finishedBuildJobs: FinishedBuildJob[] = [];

    /** Whether the build agent filter should be shown and editable */
    readonly buildAgentFilterable = signal(false);

    /**
     * Initializes the component, reading the inputs provided via the dialog configuration data.
     */
    ngOnInit() {
        const data = this.dialogConfig?.data;
        if (data?.finishedBuildJobFilter) {
            // Clone the incoming filter so that edits made in the dialog are isolated from the parent until the user confirms.
            // On cancel the parent keeps its original filter; on confirm the cloned (edited) filter is returned via dialogRef.close().
            const source: FinishedBuildJobFilter = data.finishedBuildJobFilter;
            this.finishedBuildJobFilter = Object.assign(new FinishedBuildJobFilter(source.buildAgentAddress), source, { appliedFilters: new Map(source.appliedFilters) });
        } else {
            this.finishedBuildJobFilter = new FinishedBuildJobFilter();
        }
        if (data?.buildAgentFilterable !== undefined) {
            this.buildAgentFilterable.set(data.buildAgentFilterable);
        }
        if (data?.finishedBuildJobs) {
            this.finishedBuildJobs = data.finishedBuildJobs;
        }
        this.buildStatusFilterValues = Object.values(BuildJobStatusFilter);
    }

    /**
     * Get all build agents' addresses from the finished build jobs.
     */
    get buildAgentAddresses(): string[] {
        return Array.from(new Set(this.finishedBuildJobs.map((buildJob) => buildJob.buildAgentAddress ?? '').filter((address) => address !== '')));
    }

    /**
     * Called by p-autoComplete on each keystroke/focus to populate the build agent address suggestions.
     * @param event the autocomplete complete event carrying the current query
     */
    searchBuildAgentAddresses(event: AutoCompleteCompleteEvent): void {
        const term = event.query;
        const buildAgentAddresses = this.buildAgentAddresses;
        const filtered = (term === '' ? buildAgentAddresses : buildAgentAddresses.filter((v) => v.toLowerCase().indexOf(term.toLowerCase()) > -1)).slice(0, 10);
        this.buildAgentAddressSuggestions.set(filtered);
    }

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
        this.commitFinishedBuildJobFilter();
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
        this.commitFinishedBuildJobFilter();
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
        this.commitFinishedBuildJobFilter();
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
        this.commitFinishedBuildJobFilter();
    }

    /**
     * Closes the modal without applying any filter changes.
     */
    cancel() {
        this.dialogRef.close();
    }

    confirm() {
        this.dialogRef.close(this.finishedBuildJobFilter);
    }
}
