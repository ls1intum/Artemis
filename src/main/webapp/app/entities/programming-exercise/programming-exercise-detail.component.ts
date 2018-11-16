import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ProgrammingExercise } from './programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html'
})
export class ProgrammingExerciseDetailComponent implements OnInit {
    programmingExercise: ProgrammingExercise;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
        });
    }

    previousState() {
        window.history.back();
    }
}
