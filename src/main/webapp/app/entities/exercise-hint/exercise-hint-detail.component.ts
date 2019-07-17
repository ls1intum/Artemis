import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

@Component({
    selector: 'jhi-exercise-hint-detail',
    templateUrl: './exercise-hint-detail.component.html',
})
export class ExerciseHintDetailComponent implements OnInit, OnDestroy {
    exerciseId: number;
    exerciseHint: IExerciseHint;

    paramSub: Subscription;

    constructor(protected route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseId = params['exerciseId'];
        });
        this.route.data.subscribe(({ exerciseHint }) => {
            this.exerciseHint = exerciseHint;
        });
    }

    ngOnDestroy(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    previousState() {
        window.history.back();
    }
}
