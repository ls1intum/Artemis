import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';
import { DragAndDropQuestionStatisticService } from './drag-and-drop-question-statistic.service';

@Component({
    selector: 'jhi-drag-and-drop-question-statistic-update',
    templateUrl: './drag-and-drop-question-statistic-update.component.html'
})
export class DragAndDropQuestionStatisticUpdateComponent implements OnInit {
    dragAndDropQuestionStatistic: IDragAndDropQuestionStatistic;
    isSaving: boolean;

    constructor(private dragAndDropQuestionStatisticService: DragAndDropQuestionStatisticService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dragAndDropQuestionStatistic }) => {
            this.dragAndDropQuestionStatistic = dragAndDropQuestionStatistic;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.dragAndDropQuestionStatistic.id !== undefined) {
            this.subscribeToSaveResponse(this.dragAndDropQuestionStatisticService.update(this.dragAndDropQuestionStatistic));
        } else {
            this.subscribeToSaveResponse(this.dragAndDropQuestionStatisticService.create(this.dragAndDropQuestionStatistic));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDragAndDropQuestionStatistic>>) {
        result.subscribe(
            (res: HttpResponse<IDragAndDropQuestionStatistic>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError()
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
