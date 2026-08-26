import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';

import { ITEMS_PER_PAGE } from 'app/foundation/constants/pagination.constants';
import { Audit } from './audit.model';
import { AuditsQuery, AuditsService } from './audits.service';
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
import { TumUiMessageComponent, TumUiPaginatorComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTableDirective, TumUiTabsComponent } from '@tumaet/ui-angular';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';

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
        TumUiTableDirective,
        FormDateTimePickerComponent,
        TumUiTabsComponent,
        TumUiTabListComponent,
        TumUiTabComponent,
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
     * Payload keys the generic list leaves out: `message` and `remoteAddress` are rendered above with their own
     * formatting, and `sessionId` identifies a session rather than describing the event, so it has no place on screen.
     */
    private static readonly SEPARATELY_RENDERED_DATA_KEYS = new Set(['message', 'remoteAddress', 'sessionId']);

    /**
     * The rows the table renders: each audit plus the entries of its `data` payload that the row does not already show
     * in a column of its own.
     *
     * Precomputed rather than derived in the template, because only the general log's payload has a known shape. A
     * domain action records its own keys (`course`, `exerciseId`, ...) and an account security event records e.g.
     * `reason`, so a column that only rendered `message` and `remoteAddress` would leave the Application and Security
     * tabs with an empty Extra data column for almost every row.
     *
     * A falsy value is dropped rather than rendered as a dangling `key:` label; the server stores the payload as
     * strings, so an absent entry arrives as an empty string, `null` or `undefined` depending on the event.
     */
    readonly auditRows = computed(() =>
        this.audits().map((audit) => ({
            audit,
            otherData: Object.entries(audit.data ?? {})
                .filter(([key, value]) => !AuditsComponent.SEPARATELY_RENDERED_DATA_KEYS.has(key) && !!value)
                .map(([key, value]) => ({ key, value: value as string })),
        })),
    );

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

    /**
     * Whether a complete date range is set. Filtering is applied only then; with one or both dates cleared the page
     * shows every audit, which is what clearing a filter is expected to mean. The server matches that shape: its
     * filtered endpoint requires both dates, and the plain one returns all of them.
     */
    readonly hasDateRange = computed(() => this.fromDate() !== '' && this.toDate() !== '');

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
        void this.router.navigate(['/admin/audits'], {
            queryParams: {
                page: this.page(),
                sort: this.predicate() + ',' + (this.ascending() ? 'asc' : 'desc'),
                // Left out rather than sent empty, so the URL reads as "no date filter" and `handleNavigation` does
                // not try to parse a blank value back into a date.
                from: this.fromDate() || undefined,
                to: this.toDate() || undefined,
                logType: this.logType(),
            },
        });
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
     * @param value the selected tab value, as emitted by the tabs component. Its emitted type includes `undefined`,
     *                  which is ignored here: only a value matching a known log triggers a switch.
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
     * component's 1-indexed page.
     */
    onPageChange(page: number): void {
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
            /*
             * Read the two dates together rather than one at a time. A URL that carries only one of them is what
             * clearing a picker produces, and the page treats a half-filled range as no filter at all
             * (`hasDateRange`). Setting them independently would leave the missing half on the default `ngOnInit`
             * seeded, pair it with the surviving date, and reload the very filter the user had just cleared. Only a
             * URL carrying neither keeps those defaults, which is what a plain visit to the page gets.
             */
            const from = params.get('from');
            const to = params.get('to');
            if (from || to) {
                this.fromDate.set(from ? this.datePipe.transform(from, this.dateFormat)! : '');
                this.toDate.set(to ? this.datePipe.transform(to, this.dateFormat)! : '');
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
        const query: AuditsQuery = {
            page: this.page() - 1,
            size: this.itemsPerPage,
            sort: this.sort(),
            logType: this.logType(),
        };
        // Both dates or neither: the filtered endpoint is only matched when both parameters are present, and
        // sending a blank one would reach it with a value that cannot be parsed as a date.
        if (this.hasDateRange()) {
            query.fromDate = this.fromDate();
            query.toDate = this.toDate();
        }
        this.auditsService.query(query).subscribe((res: HttpResponse<Audit[]>) => this.onSuccess(res.body, res.headers));
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
