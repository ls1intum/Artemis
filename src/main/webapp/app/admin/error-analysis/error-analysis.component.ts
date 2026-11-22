import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, catchError, switchMap, tap } from 'rxjs';

import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumAetUiPaginatorComponent,
    TumAetUiSelectButtonComponent,
    TumAetUiTableDirective,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { AlertService } from 'app/foundation/service/alert.service';

import { ErrorAnalysisService } from './error-analysis.service';
import {
    DEFAULT_ERROR_ANALYSIS_SPAN,
    DEFAULT_ERROR_LOCATION_FILTER,
    DEFAULT_ERROR_PAGE_SIZE,
    DEFAULT_ERROR_SEVERITY_FILTER,
    DEFAULT_ERROR_SORT_FIELD,
    ERROR_ANALYSIS_SPANS,
    ERROR_LOCATION_FILTERS,
    ERROR_PAGE_SIZES,
    ERROR_SEVERITY_FILTERS,
    ERROR_SORT_FIELDS,
    ErrorAnalysisPage,
    ErrorAnalysisSpan,
    ErrorLocationFilter,
    ErrorSeverityFilter,
    ErrorSortField,
} from './error-analysis.model';

/**
 * Admin page for the error analysis: the errors of the server and the clients that are alike as one row, with how often they
 * happened, when they happened first and last, and how serious they are.
 *
 * The server sorts the rows and cuts them into pages, so a large installation neither computes nor sends all of them at once. Every
 * change of what is looked at or of the sort order starts again at the first page.
 */
@Component({
    selector: 'jhi-error-analysis',
    templateUrl: './error-analysis.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TumAetUiPaginatorComponent,
        TumAetUiSelectButtonComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiTagComponent,
    ],
})
export class ErrorAnalysisComponent {
    private readonly errorAnalysisService = inject(ErrorAnalysisService);
    private readonly alertService = inject(AlertService);

    protected readonly faSpinner = faSpinner;

    protected readonly spanOptions = ERROR_ANALYSIS_SPANS.map((span) => ({ label: `artemisApp.errorAnalysis.span.${span}`, value: span }));
    protected readonly locationOptions = ERROR_LOCATION_FILTERS.map((location) => ({ label: `artemisApp.errorAnalysis.location.${location}`, value: location }));
    protected readonly severityOptions = ERROR_SEVERITY_FILTERS.map((severity) => ({ label: `artemisApp.errorAnalysis.severity.${severity}`, value: severity }));
    protected readonly pageSizeOptions = ERROR_PAGE_SIZES;
    protected readonly sortFields = ERROR_SORT_FIELDS;

    readonly span = signal<ErrorAnalysisSpan>(DEFAULT_ERROR_ANALYSIS_SPAN);
    readonly location = signal<ErrorLocationFilter>(DEFAULT_ERROR_LOCATION_FILTER);
    readonly minSeverity = signal<ErrorSeverityFilter>(DEFAULT_ERROR_SEVERITY_FILTER);
    readonly sortField = signal<ErrorSortField>(DEFAULT_ERROR_SORT_FIELD);
    readonly sortAscending = signal<boolean>(false);
    /** The number of the page that is shown, the first page is 0. */
    readonly page = signal<number>(0);
    readonly pageSize = signal<number>(DEFAULT_ERROR_PAGE_SIZE);

    readonly loading = signal<boolean>(true);
    /** `undefined` until the first answer arrives. */
    readonly result = signal<ErrorAnalysisPage | undefined>(undefined);

    readonly groups = computed(() => this.result()?.groups ?? []);
    readonly totalGroups = computed(() => this.result()?.totalGroups ?? 0);
    readonly totalErrors = computed(() => this.result()?.totalErrors ?? 0);

    private readonly query = computed(() => ({
        span: this.span(),
        location: this.location(),
        minSeverity: this.minSeverity(),
        sortField: this.sortField(),
        ascending: this.sortAscending(),
        page: this.page(),
        pageSize: this.pageSize(),
    }));

    constructor() {
        // A choice can change faster than the server answers: switchMap drops the answer to the query that was replaced, because a
        // slower earlier answer arriving last would otherwise show the rows of a query that the controls no longer select.
        toObservable(this.query)
            .pipe(
                tap(() => this.loading.set(true)),
                switchMap((query) =>
                    this.errorAnalysisService.getGroups(query).pipe(
                        catchError(() => {
                            this.alertService.error('artemisApp.errorAnalysis.loadError');
                            this.loading.set(false);
                            return EMPTY;
                        }),
                    ),
                ),
                takeUntilDestroyed(),
            )
            .subscribe((result) => {
                const lastPage = Math.max(0, Math.ceil(result.totalGroups / this.pageSize()) - 1);
                if (this.page() > lastPage) {
                    // the errors of a later page have gone since it was shown, for example because the time span moved on
                    this.page.set(lastPage);
                    return;
                }
                this.result.set(result);
                this.loading.set(false);
            });
    }

    protected setSpan(span: ErrorAnalysisSpan): void {
        this.span.set(span);
        this.page.set(0);
    }

    protected setLocation(location: ErrorLocationFilter): void {
        this.location.set(location);
        this.page.set(0);
    }

    protected setMinSeverity(minSeverity: ErrorSeverityFilter): void {
        this.minSeverity.set(minSeverity);
        this.page.set(0);
    }

    protected onSort(event: TumAetUiTableSortEvent): void {
        this.sortField.set(event.field as ErrorSortField);
        this.sortAscending.set(event.order === 1);
        this.page.set(0);
    }

    protected onPageChange(page: number): void {
        this.page.set(page);
    }

    protected onPageSizeChange(pageSize: number): void {
        this.pageSize.set(pageSize);
        this.page.set(0);
    }

    protected severityTag(severity: ErrorSeverityFilter): 'warn' | 'danger' {
        return severity === 'WARN' ? 'warn' : 'danger';
    }
}
