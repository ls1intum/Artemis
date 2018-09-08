import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';
import { IDragItem } from 'app/shared/model/drag-item.model';
import { DragItemService } from 'app/entities/drag-item';
import { IDropLocation } from 'app/shared/model/drop-location.model';
import { DropLocationService } from 'app/entities/drop-location';
import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from 'app/entities/drag-and-drop-submitted-answer';

@Component({
    selector: 'jhi-drag-and-drop-assignment-update',
    templateUrl: './drag-and-drop-assignment-update.component.html'
})
export class DragAndDropAssignmentUpdateComponent implements OnInit {
    private _dragAndDropAssignment: IDragAndDropAssignment;
    isSaving: boolean;

    dragitems: IDragItem[];

    droplocations: IDropLocation[];

    draganddropsubmittedanswers: IDragAndDropSubmittedAnswer[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        private dragItemService: DragItemService,
        private dropLocationService: DropLocationService,
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dragAndDropAssignment }) => {
            this.dragAndDropAssignment = dragAndDropAssignment;
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
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.dragAndDropAssignment.id !== undefined) {
            this.subscribeToSaveResponse(this.dragAndDropAssignmentService.update(this.dragAndDropAssignment));
        } else {
            this.subscribeToSaveResponse(this.dragAndDropAssignmentService.create(this.dragAndDropAssignment));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDragAndDropAssignment>>) {
        result.subscribe(
            (res: HttpResponse<IDragAndDropAssignment>) => this.onSaveSuccess(),
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
    get dragAndDropAssignment() {
        return this._dragAndDropAssignment;
    }

    set dragAndDropAssignment(dragAndDropAssignment: IDragAndDropAssignment) {
        this._dragAndDropAssignment = dragAndDropAssignment;
    }
}
