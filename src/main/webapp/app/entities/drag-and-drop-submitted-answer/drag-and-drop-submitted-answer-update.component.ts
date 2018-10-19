import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-update',
    templateUrl: './drag-and-drop-submitted-answer-update.component.html'
})
export class DragAndDropSubmittedAnswerUpdateComponent implements OnInit {
    dragAndDropSubmittedAnswer: IDragAndDropSubmittedAnswer;
    isSaving: boolean;

    constructor(private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dragAndDropSubmittedAnswer }) => {
            this.dragAndDropSubmittedAnswer = dragAndDropSubmittedAnswer;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.dragAndDropSubmittedAnswer.id !== undefined) {
            this.subscribeToSaveResponse(this.dragAndDropSubmittedAnswerService.update(this.dragAndDropSubmittedAnswer));
        } else {
            this.subscribeToSaveResponse(this.dragAndDropSubmittedAnswerService.create(this.dragAndDropSubmittedAnswer));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDragAndDropSubmittedAnswer>>) {
        result.subscribe(
            (res: HttpResponse<IDragAndDropSubmittedAnswer>) => this.onSaveSuccess(),
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
