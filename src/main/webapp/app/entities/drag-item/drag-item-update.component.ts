import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IDragItem } from 'app/shared/model/drag-item.model';
import { DragItemService } from './drag-item.service';
import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { DragAndDropQuestionService } from 'app/entities/drag-and-drop-question';

@Component({
    selector: 'jhi-drag-item-update',
    templateUrl: './drag-item-update.component.html'
})
export class DragItemUpdateComponent implements OnInit {
    dragItem: IDragItem;
    isSaving: boolean;

    draganddropquestions: IDragAndDropQuestion[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private dragItemService: DragItemService,
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dragItem }) => {
            this.dragItem = dragItem;
        });
        this.dragAndDropQuestionService.query().subscribe(
            (res: HttpResponse<IDragAndDropQuestion[]>) => {
                this.draganddropquestions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.dragItem.id !== undefined) {
            this.subscribeToSaveResponse(this.dragItemService.update(this.dragItem));
        } else {
            this.subscribeToSaveResponse(this.dragItemService.create(this.dragItem));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDragItem>>) {
        result.subscribe((res: HttpResponse<IDragItem>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackDragAndDropQuestionById(index: number, item: IDragAndDropQuestion) {
        return item.id;
    }
}
