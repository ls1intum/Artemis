import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';

@Component({
    selector: 'jhi-exercise-result-detail',
    templateUrl: './exercise-result-detail.component.html'
})
export class ExerciseResultDetailComponent implements OnInit {
    exerciseResult: IExerciseResult;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ exerciseResult }) => {
            this.exerciseResult = exerciseResult;
        });
    }

    previousState() {
        window.history.back();
    }
}
