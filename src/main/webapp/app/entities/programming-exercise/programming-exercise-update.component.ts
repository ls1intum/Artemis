import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IProgrammingExercise } from 'app/shared/model/programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html'
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    private _programmingExercise: IProgrammingExercise;
    isSaving: boolean;

    constructor(private programmingExerciseService: ProgrammingExerciseService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.programmingExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.create(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IProgrammingExercise>>) {
        result.subscribe((res: HttpResponse<IProgrammingExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
    get programmingExercise() {
        return this._programmingExercise;
    }

    set programmingExercise(programmingExercise: IProgrammingExercise) {
        this._programmingExercise = programmingExercise;
    }
}
