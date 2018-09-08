import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Component({
    selector: 'jhi-drag-and-drop-question-update',
    templateUrl: './drag-and-drop-question-update.component.html'
})
export class DragAndDropQuestionUpdateComponent implements OnInit {
    private _dragAndDropQuestion: IDragAndDropQuestion;
    isSaving: boolean;

    constructor(private dragAndDropQuestionService: DragAndDropQuestionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ dragAndDropQuestion }) => {
            this.dragAndDropQuestion = dragAndDropQuestion;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.dragAndDropQuestion.id !== undefined) {
            this.subscribeToSaveResponse(this.dragAndDropQuestionService.update(this.dragAndDropQuestion));
        } else {
            this.subscribeToSaveResponse(this.dragAndDropQuestionService.create(this.dragAndDropQuestion));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IDragAndDropQuestion>>) {
        result.subscribe((res: HttpResponse<IDragAndDropQuestion>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
    get dragAndDropQuestion() {
        return this._dragAndDropQuestion;
    }

    set dragAndDropQuestion(dragAndDropQuestion: IDragAndDropQuestion) {
        this._dragAndDropQuestion = dragAndDropQuestion;
    }
}
