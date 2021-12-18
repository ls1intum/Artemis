import { Component, OnInit } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Data, ParamMap, Router } from '@angular/router';
import { combineLatest } from 'rxjs';

import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { Audit } from './audit.model';
import { AuditsService } from './audits.service';
import { faSort } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-audit',
    templateUrl: './audits.component.html',
})
export class AuditsComponent implements OnInit {
    audits?: Audit[];
    fromDate = '';
    predicate!: string;
    ascending!: boolean;
    toDate = '';

    // page information
    page = 1;
    itemsPerPage = ITEMS_PER_PAGE;
    totalItems = 0;

    private dateFormat = 'yyyy-MM-dd';

    // Icon
    faSort = faSort;

    constructor(private auditsService: AuditsService, private activatedRoute: ActivatedRoute, private datePipe: DatePipe, private router: Router) {}

    ngOnInit(): void {
        this.toDate = this.today();
        this.fromDate = this.previousMonth();
        this.handleNavigation();
    }

    canLoad(): boolean {
        return this.fromDate !== '' && this.toDate !== '';
    }

    transition(): void {
        if (this.canLoad()) {
            this.router.navigate(['/admin/audits'], {
                queryParams: {
                    page: this.page,
                    sort: this.predicate + ',' + (this.ascending ? 'asc' : 'desc'),
                    from: this.fromDate,
                    to: this.toDate,
                },
            });
        }
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
        combineLatest(this.activatedRoute.data, this.activatedRoute.queryParamMap, (data: Data, params: ParamMap) => {
            const page = params.get('page');
            this.page = page !== null ? +page : 1;
            const sort = (params.get('sort') ?? data['defaultSort']).split(',');
            this.predicate = sort[0];
            this.ascending = sort[1] === 'asc';
            if (params.get('from')) {
                this.fromDate = this.datePipe.transform(params.get('from'), this.dateFormat)!;
            }
            if (params.get('to')) {
                this.toDate = this.datePipe.transform(params.get('to'), this.dateFormat)!;
            }
            this.loadData();
        }).subscribe();
    }

    private loadData(): void {
        this.auditsService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: this.sort(),
                fromDate: this.fromDate,
                toDate: this.toDate,
            })
            .subscribe((res: HttpResponse<Audit[]>) => this.onSuccess(res.body, res.headers));
    }

    private sort(): string[] {
        const result = [this.predicate + ',' + (this.ascending ? 'asc' : 'desc')];
        if (this.predicate !== 'id') {
            result.push('id');
        }
        return result;
    }

    private onSuccess(audits: Audit[] | null, headers: HttpHeaders): void {
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.audits = audits || [];
    }
}
