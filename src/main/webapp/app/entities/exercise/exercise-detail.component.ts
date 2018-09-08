import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IExercise } from 'app/shared/model/exercise.model';

@Component({
    selector: 'jhi-exercise-detail',
    templateUrl: './exercise-detail.component.html'
})
export class ExerciseDetailComponent implements OnInit {
    exercise: IExercise;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
        });
    }

    previousState() {
        window.history.back();
    }
}
