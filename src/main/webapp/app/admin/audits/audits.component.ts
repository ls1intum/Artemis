import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';

import { ITEMS_PER_PAGE } from 'app/foundation/constants/pagination.constants';
import { Audit } from './audit.model';
import { AuditsService } from './audits.service';
import { AuditLogType } from './audit-log-type.model';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ItemCountComponent } from 'app/foundation/pagination/item-count.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { TumUiPaginatorComponent } from 'app/shared-ui/tum-ui/paginator/tum-ui-paginator.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TumUiInputGroupComponent } from 'app/shared-ui/tum-ui/input-group/tum-ui-input-group.component';
import { TumUiInputGroupAddonComponent } from 'app/shared-ui/tum-ui/input-group/tum-ui-input-group-addon.component';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TabsModule } from 'primeng/tabs';

/**
 * Admin component for viewing system audit logs.
 * Shows audit events with filtering by date range and pagination.
 */
@Component({
    selector: 'jhi-audit',
    templateUrl: './audits.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        FormsModule,
        SortDirective,
        SortByDirective,
        FaIconComponent,
        ItemCountComponent,
        TumUiPaginatorComponent,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        TumUiMessageComponent,
        TumUiInputGroupComponent,
        TumUiInputGroupAddonComponent,
        FormDateTimePickerComponent,
        TabsModule,
    ],
})
export class AuditsComponent implements OnInit {
    private readonly auditsService = inject(AuditsService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly datePipe = inject(DatePipe);
    private readonly router = inject(Router);

    /** Audit log entries */
    readonly audits = signal<Audit[]>([]);

    /**
     * Which of the three audit logs is shown. Each is a separate table with its own retention period, so switching tabs
     * queries a different (and much smaller) table rather than filtering one large one.
     */
    readonly logType = signal<AuditLogType>(AuditLogType.GENERAL);

    /** The tabs rendered above the table, in display order. */
    readonly logTypeTabs: readonly { value: AuditLogType; labelKey: string }[] = [
        { value: AuditLogType.GENERAL, labelKey: 'audits.logType.general' },
        { value: AuditLogType.SECURITY, labelKey: 'audits.logType.security' },
        { value: AuditLogType.APPLICATION, labelKey: 'audits.logType.application' },
    ];

    /** Date range filter - from date */
    readonly fromDate = signal('');

    /** Date range filter - to date */
    readonly toDate = signal('');

    /** Sort predicate */
    readonly predicate = signal('');

    /** Sort direction */
    readonly ascending = signal(true);

    /** Current page number */
    readonly page = signal(1);

    /** Total number of items */
    readonly totalItems = signal(0);

    /** Items per page */
    readonly itemsPerPage = ITEMS_PER_PAGE;

    /** Whether data can be loaded (date range is valid) */
    readonly canLoad = computed(() => this.fromDate() !== '' && this.toDate() !== '');

    /** From date exposed to the shared date picker as a native Date (the wrapper's value contract). */
    readonly fromDateValue = computed(() => this.toPickerDate(this.fromDate()));

    /** To date exposed to the shared date picker as a native Date (the wrapper's value contract). */
    readonly toDateValue = computed(() => this.toPickerDate(this.toDate()));

    private readonly dateFormat = 'yyyy-MM-dd';

    protected readonly faSort = faSort;
    protected readonly DateTimePickerType = DateTimePickerType;

    ngOnInit(): void {
        this.toDate.set(this.today());
        this.fromDate.set(this.previousMonth());
        this.handleNavigation();
    }

    transition(): void {
        if (this.canLoad()) {
            void this.router.navigate(['/admin/audits'], {
                queryParams: {
                    page: this.page(),
                    sort: this.predicate() + ',' + (this.ascending() ? 'asc' : 'desc'),
                    from: this.fromDate(),
                    to: this.toDate(),
                    logType: this.logType(),
                },
            });
        } else {
            // Incomplete date range (e.g. a date was just deselected): drop the previously loaded results so the
            // table and paginator don't linger with stale, un-clickable pages — the paginator's page change is a
            // no-op while the range is incomplete, which otherwise stranded the user on a dead multi-page view.
            this.audits.set([]);
            this.totalItems.set(0);
        }
    }

    /**
     * Updates the from date filter from the shared date picker value.
     * The picker emits a dayjs/Date (or null); the audits service consumes yyyy-MM-dd strings, so convert at the boundary.
     */
    updateFromDate(value: dayjs.Dayjs | Date | null | undefined): void {
        this.fromDate.set(this.toDateString(value));
    }

    /**
     * Updates the to date filter from the shared date picker value.
     * See {@link updateFromDate} for the conversion rationale.
     */
    updateToDate(value: dayjs.Dayjs | Date | null | undefined): void {
        this.toDate.set(this.toDateString(value));
    }

    /** Converts a picker value (dayjs/Date) to the yyyy-MM-dd string the audits service expects. */
    private toDateString(value: dayjs.Dayjs | Date | null | undefined): string {
        if (value == undefined) {
            return '';
        }
        const parsed = dayjs(value);
        // dayjs format tokens differ from Angular's DatePipe (`this.dateFormat`): year/day are UPPERCASE
        // (`YYYY`/`DD`); the lowercase `yyyy`/`dd` would render literally / as the weekday and break the URL value.
        return parsed.isValid() ? parsed.format('YYYY-MM-DD') : '';
    }

    /** Converts a stored yyyy-MM-dd string to the native Date the shared date picker binds to. */
    private toPickerDate(value: string): Date | null {
        if (!value) {
            return null;
        }
        const parsed = dayjs(value);
        return parsed.isValid() ? parsed.toDate() : null;
    }

    /**
     * Switches to another audit log. Resets to the first page, because page numbers do not carry over between logs.
     *
     * @param value the selected tab value, as emitted by the PrimeNG tabs component. Its emitted type includes
     *                  `undefined`, which is ignored here: only a value matching a known log triggers a switch.
     */
    onLogTypeChange(value: string | number | undefined): void {
        const selected = value as AuditLogType;
        if (!Object.values(AuditLogType).includes(selected) || selected === this.logType()) {
            return;
        }
        this.logType.set(selected);
        this.page.set(1);
        this.transition();
    }

    /** Updates the current page */
    updatePage(value: number): void {
        this.page.set(value);
    }

    /**
     * Handles a paginator page change. The emitted page is 0-indexed, so it is converted to the
     * component's 1-indexed page. No-op while the date range is incomplete (mirrors the former disabled paginator).
     */
    onPageChange(page: number): void {
        if (!this.canLoad()) {
            return;
        }
        this.updatePage(page + 1);
        this.transition();
    }

    private previousMonth(): string {
        let date = new Date();
        if (date.getMonth() === 0) {
            date = new Date(date.getFullYear() - 1, 11, date.getDate());
        } else {
            date = new Date(date.getFullYear(), date.getMonth() - 1, date.getDate());
        }
        return this.datePipe.transform(date, this.dateFormat)!;
    }

    private today(): string {
        // Today + 1 day - needed if the current day must be included
        const date = new Date();
        date.setDate(date.getDate() + 1);
        return this.datePipe.transform(date, this.dateFormat)!;
    }

    private handleNavigation(): void {
        combineLatest({ data: this.activatedRoute.data, params: this.activatedRoute.queryParamMap }).subscribe(({ data, params }) => {
            const pageParam = params.get('page');
            this.page.set(pageParam !== null ? +pageParam : 1);
            const sort = (params.get('sort') ?? data['defaultSort']).split(',');
            this.predicate.set(sort[0]);
            this.ascending.set(sort[1] === 'asc');
            if (params.get('from')) {
                this.fromDate.set(this.datePipe.transform(params.get('from'), this.dateFormat)!);
            }
            if (params.get('to')) {
                this.toDate.set(this.datePipe.transform(params.get('to'), this.dateFormat)!);
            }
            const logTypeParam = params.get('logType');
            // Set the log on every emission rather than only for a valid parameter: Angular reuses this component across
            // query-parameter-only navigations, so leaving the signal untouched would keep querying the previously
            // selected log. An absent or unknown value (e.g. a hand-edited URL) falls back to the general log.
            const isKnownLogType = !!logTypeParam && Object.values(AuditLogType).includes(logTypeParam as AuditLogType);
            this.logType.set(isKnownLogType ? (logTypeParam as AuditLogType) : AuditLogType.GENERAL);
            this.loadData();
        });
    }

    private loadData(): void {
        this.auditsService
            .query({
                page: this.page() - 1,
                size: this.itemsPerPage,
                sort: this.sort(),
                fromDate: this.fromDate(),
                toDate: this.toDate(),
                logType: this.logType(),
            })
            .subscribe((res: HttpResponse<Audit[]>) => this.onSuccess(res.body, res.headers));
    }

    private sort(): string[] {
        const result = [this.predicate() + ',' + (this.ascending() ? 'asc' : 'desc')];
        if (this.predicate() !== 'id') {
            result.push('id');
        }
        return result;
    }

    private onSuccess(audits: Audit[] | null, headers: HttpHeaders): void {
        this.totalItems.set(Number(headers.get('X-Total-Count')));
        this.audits.set(audits || []);
    }
}
