import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

import { ExerciseHint } from 'app/entities/exercise-hint.model';

@Component({
    selector: 'jhi-exercise-hint-detail',
    templateUrl: './exercise-hint-detail.component.html',
})
export class ExerciseHintDetailComponent implements OnInit, OnDestroy {
    exerciseHint: ExerciseHint;

    courseId: number;
    exerciseId: number;

    paramSub: Subscription;

    constructor(protected route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.courseId = params['courseId'];
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
