import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';

import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { Audit } from './audit.model';
import { AuditsService } from './audits.service';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

/**
 * Admin component for viewing system audit logs.
 * Shows audit events with filtering by date range and pagination.
 */
@Component({
    selector: 'jhi-audit',
    templateUrl: './audits.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FormsModule, SortDirective, SortByDirective, FaIconComponent, ItemCountComponent, NgbPagination, ArtemisDatePipe],
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

    private readonly dateFormat = 'yyyy-MM-dd';

    protected readonly faSort = faSort;

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

    /** Updates the from date filter */
    updateFromDate(value: string): void {
        this.fromDate.set(value);
    }

    /** Updates the to date filter */
    updateToDate(value: string): void {
        this.toDate.set(value);
    }

    /** Updates the current page */
    updatePage(value: number): void {
        this.page.set(value);
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
