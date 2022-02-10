import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { faWrench } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exercise-hint-detail',
    templateUrl: './exercise-hint-detail.component.html',
})
export class ExerciseHintDetailComponent implements OnInit, OnDestroy {
    exerciseHint: ExerciseHint;

    courseId: number;
    exerciseId: number;

    paramSub: Subscription;

    // Icons
    faWrench = faWrench;

    constructor(protected route: ActivatedRoute) {}

    /**
     * Extracts the course and exercise id and the exercise hint from the route params
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.exerciseId = params['exerciseId'];
        });
        this.route.data.subscribe(({ exerciseHint }) => {
            this.exerciseHint = exerciseHint;
        });
    }

    /**
     * Unsubscribes from the param subscription
     */
    ngOnDestroy(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }
}
