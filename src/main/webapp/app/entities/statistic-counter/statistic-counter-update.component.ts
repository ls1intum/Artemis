import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IStatisticCounter } from 'app/shared/model/statistic-counter.model';
import { StatisticCounterService } from './statistic-counter.service';

@Component({
    selector: 'jhi-statistic-counter-update',
    templateUrl: './statistic-counter-update.component.html'
})
export class StatisticCounterUpdateComponent implements OnInit {
    statisticCounter: IStatisticCounter;
    isSaving: boolean;

    constructor(private statisticCounterService: StatisticCounterService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ statisticCounter }) => {
            this.statisticCounter = statisticCounter;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.statisticCounter.id !== undefined) {
            this.subscribeToSaveResponse(this.statisticCounterService.update(this.statisticCounter));
        } else {
            this.subscribeToSaveResponse(this.statisticCounterService.create(this.statisticCounter));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IStatisticCounter>>) {
        result.subscribe((res: HttpResponse<IStatisticCounter>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
