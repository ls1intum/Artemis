import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IStatistic } from 'app/shared/model/statistic.model';
import { StatisticService } from './statistic.service';

@Component({
    selector: 'jhi-statistic-update',
    templateUrl: './statistic-update.component.html'
})
export class StatisticUpdateComponent implements OnInit {
    statistic: IStatistic;
    isSaving: boolean;

    constructor(private statisticService: StatisticService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ statistic }) => {
            this.statistic = statistic;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.statistic.id !== undefined) {
            this.subscribeToSaveResponse(this.statisticService.update(this.statistic));
        } else {
            this.subscribeToSaveResponse(this.statisticService.create(this.statistic));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IStatistic>>) {
        result.subscribe((res: HttpResponse<IStatistic>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
