import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';

@Component({
    selector: 'jhi-exercise-result-detail',
    templateUrl: './exercise-result-detail.component.html',
})
export class ExerciseResultDetailComponent implements OnInit {
    exerciseResult: IExerciseResult | null = null;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ exerciseResult }) => (this.exerciseResult = exerciseResult));
    }

    previousState(): void {
        window.history.back();
    }
}
