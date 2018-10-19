import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IDragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';
import { DragAndDropMappingService } from './drag-and-drop-mapping.service';
import { IDragItem } from 'app/shared/model/drag-item.model';
import { DragItemService } from 'app/entities/drag-item';
import { IDropLocation } from 'app/shared/model/drop-location.model';
import { DropLocationService } from 'app/entities/drop-location';
import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from 'app/entities/drag-and-drop-submitted-answer';
import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { DragAndDropQuestionService } from 'app/entities/drag-and-drop-question';

@Component({
    selector: 'jhi-drag-and-drop-mapping-update',
    templateUrl: './drag-and-drop-mapping-update.component.html'
})
export class DragAndDropMappingUpdateComponent implements OnInit {
    dragAndDropMapping: IDragAndDropMapping;
    isSaving: boolean;

    dragitems: IDragItem[];

    droplocations: IDropLocation[];

    draganddropsubmittedanswers: IDragAndDropSubmittedAnswer[];

    draganddropquestions: IDragAndDropQuestion[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private dragAndDropMappingService: DragAndDropMappingService,
        private dragItemService: DragItemService,
        private dropLocationService: DropLocationService,
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dragAndDropMapping }) => {
            this.dragAndDropMapping = dragAndDropMapping;
        });
        this.dragItemService.query().subscribe(
            (res: HttpResponse<IDragItem[]>) => {
                this.dragitems = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.dropLocationService.query().subscribe(
            (res: HttpResponse<IDropLocation[]>) => {
                this.droplocations = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.dragAndDropSubmittedAnswerService.query().subscribe(
            (res: HttpResponse<IDragAndDropSubmittedAnswer[]>) => {
                this.draganddropsubmittedanswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
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
        if (this.dragAndDropMapping.id !== undefined) {
            this.subscribeToSaveResponse(this.dragAndDropMappingService.update(this.dragAndDropMapping));
        } else {
            this.subscribeToSaveResponse(this.dragAndDropMappingService.create(this.dragAndDropMapping));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDragAndDropMapping>>) {
        result.subscribe((res: HttpResponse<IDragAndDropMapping>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackDragItemById(index: number, item: IDragItem) {
        return item.id;
    }

    trackDropLocationById(index: number, item: IDropLocation) {
        return item.id;
    }

    trackDragAndDropSubmittedAnswerById(index: number, item: IDragAndDropSubmittedAnswer) {
        return item.id;
    }

    trackDragAndDropQuestionById(index: number, item: IDragAndDropQuestion) {
        return item.id;
    }
}
