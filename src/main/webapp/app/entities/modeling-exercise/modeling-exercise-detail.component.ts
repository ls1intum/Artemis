import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IModelingExercise } from 'app/shared/model/modeling-exercise.model';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html'
})
export class ModelingExerciseDetailComponent implements OnInit {
    modelingExercise: IModelingExercise;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;
        });
    }

    previousState() {
        window.history.back();
    }
}
