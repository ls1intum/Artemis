import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IModelingExercise } from 'app/shared/model/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html'
})
export class ModelingExerciseUpdateComponent implements OnInit {
    modelingExercise: IModelingExercise;
    isSaving: boolean;

    constructor(private modelingExerciseService: ModelingExerciseService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.modelingExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.modelingExerciseService.update(this.modelingExercise));
        } else {
            this.subscribeToSaveResponse(this.modelingExerciseService.create(this.modelingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IModelingExercise>>) {
        result.subscribe((res: HttpResponse<IModelingExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
