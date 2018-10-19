import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ITextExercise } from 'app/shared/model/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html'
})
export class TextExerciseUpdateComponent implements OnInit {
    textExercise: ITextExercise;
    isSaving: boolean;

    constructor(private textExerciseService: TextExerciseService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.textExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.textExerciseService.update(this.textExercise));
        } else {
            this.subscribeToSaveResponse(this.textExerciseService.create(this.textExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ITextExercise>>) {
        result.subscribe((res: HttpResponse<ITextExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
