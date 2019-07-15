import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

@Component({
    selector: 'jhi-exercise-hint-detail',
    templateUrl: './exercise-hint-detail.component.html',
})
export class ExerciseHintDetailComponent implements OnInit {
    exerciseHint: IExerciseHint;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ exerciseHint }) => {
            this.exerciseHint = exerciseHint;
        });
    }

    previousState() {
        window.history.back();
    }
}
