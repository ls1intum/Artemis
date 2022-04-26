import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';

@Component({
    selector: 'jhi-exercise-hint-detail',
    templateUrl: './exercise-hint-detail.component.html',
})
export class ExerciseHintDetailComponent implements OnInit, OnDestroy {
    readonly HintType = HintType;

    exerciseHint: ExerciseHint;
    solutionEntries: ProgrammingExerciseSolutionEntry[];

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
        this.setSortedSolutionEntriesForCodeHint();
    }

    /**
     * Unsubscribes from the param subscription
     */
    ngOnDestroy(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    setSortedSolutionEntriesForCodeHint() {
        if (this.exerciseHint.type !== HintType.CODE) {
            this.solutionEntries = [];
            return;
        }
        const codeHint = this.exerciseHint as CodeHint;
        this.solutionEntries =
            codeHint.solutionEntries?.sort((a, b) => {
                return a.filePath?.localeCompare(b.filePath!) || a.line! - b.line!;
            }) ?? [];
    }
}
