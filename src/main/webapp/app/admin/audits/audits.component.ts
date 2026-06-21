import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';

import { ITEMS_PER_PAGE } from 'app/foundation/constants/pagination.constants';
import { Audit } from './audit.model';
import { AuditsService } from './audits.service';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ItemCountComponent } from 'app/foundation/pagination/item-count.component';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { MessageModule } from 'primeng/message';
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
        PaginatorModule,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        InputGroupModule,
        InputGroupAddonModule,
        MessageModule,
        FormDateTimePickerComponent,
    ],
})
export class AuditsComponent implements OnInit {
    private readonly auditsService = inject(AuditsService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly datePipe = inject(DatePipe);
    private readonly router = inject(Router);

    /** Audit log entries */
    readonly audits = signal<Audit[]>([]);

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
            this.router.navigate(['/admin/audits'], {
                queryParams: {
                    page: this.page(),
                    sort: this.predicate() + ',' + (this.ascending() ? 'asc' : 'desc'),
                    from: this.fromDate(),
                    to: this.toDate(),
                },
            });
        }
    }

    /**
     * Updates the from date filter from the shared date picker value.
     * The picker emits a dayjs/Date (or null); the audits service consumes yyyy-MM-dd strings,
     * so we convert at the boundary and keep the rest of the date handling untouched.
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
        return parsed.isValid() ? parsed.format(this.dateFormat) : '';
    }

    /** Converts a stored yyyy-MM-dd string to the native Date the shared date picker binds to. */
    private toPickerDate(value: string): Date | null {
        if (!value) {
            return null;
        }
        const parsed = dayjs(value);
        return parsed.isValid() ? parsed.toDate() : null;
    }

    /** Updates the current page */
    updatePage(value: number): void {
        this.page.set(value);
    }

    /**
     * Handles a PrimeNG paginator page change. The event page is 0-indexed, so it is converted to the
     * component's 1-indexed page. No-op while the date range is incomplete (mirrors the former disabled paginator).
     */
    onPageChange(event: PaginatorState): void {
        if (!this.canLoad()) {
            return;
        }
        this.updatePage((event.page ?? 0) + 1);
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
