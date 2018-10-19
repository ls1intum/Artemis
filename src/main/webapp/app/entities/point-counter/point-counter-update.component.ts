import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IPointCounter } from 'app/shared/model/point-counter.model';
import { PointCounterService } from './point-counter.service';
import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';
import { QuizPointStatisticService } from 'app/entities/quiz-point-statistic';

@Component({
    selector: 'jhi-point-counter-update',
    templateUrl: './point-counter-update.component.html'
})
export class PointCounterUpdateComponent implements OnInit {
    pointCounter: IPointCounter;
    isSaving: boolean;

    quizpointstatistics: IQuizPointStatistic[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private pointCounterService: PointCounterService,
        private quizPointStatisticService: QuizPointStatisticService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ pointCounter }) => {
            this.pointCounter = pointCounter;
        });
        this.quizPointStatisticService.query().subscribe(
            (res: HttpResponse<IQuizPointStatistic[]>) => {
                this.quizpointstatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.pointCounter.id !== undefined) {
            this.subscribeToSaveResponse(this.pointCounterService.update(this.pointCounter));
        } else {
            this.subscribeToSaveResponse(this.pointCounterService.create(this.pointCounter));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IPointCounter>>) {
        result.subscribe((res: HttpResponse<IPointCounter>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackQuizPointStatisticById(index: number, item: IQuizPointStatistic) {
        return item.id;
    }
}
