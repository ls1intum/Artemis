import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IDropLocation } from 'app/shared/model/drop-location.model';
import { DropLocationService } from './drop-location.service';
import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { DragAndDropQuestionService } from 'app/entities/drag-and-drop-question';

@Component({
    selector: 'jhi-drop-location-update',
    templateUrl: './drop-location-update.component.html'
})
export class DropLocationUpdateComponent implements OnInit {
    private _dropLocation: IDropLocation;
    isSaving: boolean;

    draganddropquestions: IDragAndDropQuestion[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private dropLocationService: DropLocationService,
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dropLocation }) => {
            this.dropLocation = dropLocation;
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
        if (this.dropLocation.id !== undefined) {
            this.subscribeToSaveResponse(this.dropLocationService.update(this.dropLocation));
        } else {
            this.subscribeToSaveResponse(this.dropLocationService.create(this.dropLocation));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDropLocation>>) {
        result.subscribe((res: HttpResponse<IDropLocation>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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
    get dropLocation() {
        return this._dropLocation;
    }

    set dropLocation(dropLocation: IDropLocation) {
        this._dropLocation = dropLocation;
    }
}
