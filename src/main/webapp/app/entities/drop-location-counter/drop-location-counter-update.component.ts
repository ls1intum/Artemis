import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IDropLocationCounter } from 'app/shared/model/drop-location-counter.model';
import { DropLocationCounterService } from './drop-location-counter.service';
import { IDropLocation } from 'app/shared/model/drop-location.model';
import { DropLocationService } from 'app/entities/drop-location';
import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';
import { DragAndDropQuestionStatisticService } from 'app/entities/drag-and-drop-question-statistic';

@Component({
    selector: 'jhi-drop-location-counter-update',
    templateUrl: './drop-location-counter-update.component.html'
})
export class DropLocationCounterUpdateComponent implements OnInit {
    dropLocationCounter: IDropLocationCounter;
    isSaving: boolean;

    droplocations: IDropLocation[];

    draganddropquestionstatistics: IDragAndDropQuestionStatistic[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private dropLocationCounterService: DropLocationCounterService,
        private dropLocationService: DropLocationService,
        private dragAndDropQuestionStatisticService: DragAndDropQuestionStatisticService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dropLocationCounter }) => {
            this.dropLocationCounter = dropLocationCounter;
        });
        this.dropLocationService.query({ filter: 'droplocationcounter-is-null' }).subscribe(
            (res: HttpResponse<IDropLocation[]>) => {
                if (!this.dropLocationCounter.dropLocation || !this.dropLocationCounter.dropLocation.id) {
                    this.droplocations = res.body;
                } else {
                    this.dropLocationService.find(this.dropLocationCounter.dropLocation.id).subscribe(
                        (subRes: HttpResponse<IDropLocation>) => {
                            this.droplocations = [subRes.body].concat(res.body);
                        },
                        (subRes: HttpErrorResponse) => this.onError(subRes.message)
                    );
                }
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.dragAndDropQuestionStatisticService.query().subscribe(
            (res: HttpResponse<IDragAndDropQuestionStatistic[]>) => {
                this.draganddropquestionstatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.dropLocationCounter.id !== undefined) {
            this.subscribeToSaveResponse(this.dropLocationCounterService.update(this.dropLocationCounter));
        } else {
            this.subscribeToSaveResponse(this.dropLocationCounterService.create(this.dropLocationCounter));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDropLocationCounter>>) {
        result.subscribe((res: HttpResponse<IDropLocationCounter>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackDropLocationById(index: number, item: IDropLocation) {
        return item.id;
    }

    trackDragAndDropQuestionStatisticById(index: number, item: IDragAndDropQuestionStatistic) {
        return item.id;
    }
}
