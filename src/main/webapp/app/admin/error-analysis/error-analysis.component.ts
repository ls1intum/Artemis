import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, catchError, switchMap, tap } from 'rxjs';

import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiSelectButtonComponent, TumAetUiTableDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';

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
    DEFAULT_ERROR_SEVERITY_FILTER,
    ERROR_ANALYSIS_SPANS,
    ERROR_LOCATION_FILTERS,
    ERROR_SEVERITY_FILTERS,
    ErrorAnalysisSpan,
    ErrorGroup,
    ErrorLocationFilter,
    ErrorSeverityFilter,
} from './error-analysis.model';

/**
 * Admin page for the error analysis: the errors of the server and the clients that are alike as one row, with how often they
 * happened, when they happened first and last, and how serious they are.
 *
 * The analysis lives in the memory of the node that answers and starts again when it restarts, which the page says so that a
 * quiet page is not mistaken for a healthy system.
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
        TumAetUiSelectButtonComponent,
        TumAetUiTableDirective,
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

    readonly span = signal<ErrorAnalysisSpan>(DEFAULT_ERROR_ANALYSIS_SPAN);
    readonly location = signal<ErrorLocationFilter>(DEFAULT_ERROR_LOCATION_FILTER);
    readonly minSeverity = signal<ErrorSeverityFilter>(DEFAULT_ERROR_SEVERITY_FILTER);

    readonly loading = signal<boolean>(true);
    /** `undefined` until the first answer arrives. */
    readonly groups = signal<ErrorGroup[] | undefined>(undefined);

    /** The number of errors in all the groups that are shown. */
    readonly totalCount = computed(() => (this.groups() ?? []).reduce((sum, group) => sum + group.count, 0));

    private readonly filter = computed(() => ({ span: this.span(), location: this.location(), minSeverity: this.minSeverity() }));

    constructor() {
        // A filter can change faster than the server answers: switchMap drops the answer to the filter that was replaced, because a
        // slower earlier answer arriving last would otherwise show the errors of a filter the controls no longer select.
        toObservable(this.filter)
            .pipe(
                tap(() => this.loading.set(true)),
                switchMap(({ span, location, minSeverity }) =>
                    this.errorAnalysisService.getGroups(span, location, minSeverity).pipe(
                        catchError(() => {
                            this.alertService.error('artemisApp.errorAnalysis.loadError');
                            this.loading.set(false);
                            return EMPTY;
                        }),
                    ),
                ),
                takeUntilDestroyed(),
            )
            .subscribe((groups) => {
                this.groups.set(groups);
                this.loading.set(false);
            });
    }

    protected severityTag(severity: ErrorSeverityFilter): 'warn' | 'danger' {
        return severity === 'WARN' ? 'warn' : 'danger';
    }
}
