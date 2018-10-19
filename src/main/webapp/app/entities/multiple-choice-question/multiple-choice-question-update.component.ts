import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IMultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';

@Component({
    selector: 'jhi-multiple-choice-question-update',
    templateUrl: './multiple-choice-question-update.component.html'
})
export class MultipleChoiceQuestionUpdateComponent implements OnInit {
    multipleChoiceQuestion: IMultipleChoiceQuestion;
    isSaving: boolean;

    constructor(private multipleChoiceQuestionService: MultipleChoiceQuestionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ multipleChoiceQuestion }) => {
            this.multipleChoiceQuestion = multipleChoiceQuestion;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.multipleChoiceQuestion.id !== undefined) {
            this.subscribeToSaveResponse(this.multipleChoiceQuestionService.update(this.multipleChoiceQuestion));
        } else {
            this.subscribeToSaveResponse(this.multipleChoiceQuestionService.create(this.multipleChoiceQuestion));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IMultipleChoiceQuestion>>) {
        result.subscribe(
            (res: HttpResponse<IMultipleChoiceQuestion>) => this.onSaveSuccess(),
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
