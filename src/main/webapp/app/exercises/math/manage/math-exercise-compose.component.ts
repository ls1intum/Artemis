import { Subscription, takeWhile } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { Component, OnDestroy, OnInit } from '@angular/core';

import { Course } from 'app/entities/course.model';
import { MathExercise } from 'app/entities/math-exercise.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { FormBuilder, FormGroup } from '@angular/forms';

@Component({
    selector: 'jhi-math-exercise-compose',
    templateUrl: './math-exercise-compose.component.html',
})
export class MathExerciseComposeComponent implements OnInit, OnDestroy {
    protected readonly domainCommands = [new KatexCommand()];
    protected readonly EditorMode = EditorMode;

    protected mathExercise: MathExercise;
    protected course: Course | undefined;

    private eventSubscription: Subscription;
    private componentActive = true;
    protected composeForm: FormGroup;

    protected get isExamExercise(): boolean {
        return !!this.mathExercise.exerciseGroup;
    }

    constructor(
        private eventManager: EventManager,
        private route: ActivatedRoute,
        private router: Router,
        private fb: FormBuilder,
    ) {}

    /**
     * Loads the math exercise and subscribes to changes of it on component initialization.
     */
    ngOnInit() {
        this.route.data.pipe(takeWhile(() => this.componentActive)).subscribe((data) => this.initExercise(data.mathExercise));
        this.eventSubscription = this.eventManager.subscribe('mathExerciseListModification', () => this.router.navigateByUrl(this.router.url));
        this.composeForm = this.fb.group({
            problemStatement: [this.mathExercise.problemStatement],
            exampleSolution: [this.mathExercise.exampleSolution],
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscription);
        this.componentActive = false;
    }

    private initExercise(exercise: MathExercise) {
        this.mathExercise = exercise;
        this.course = this.isExamExercise ? this.mathExercise.exerciseGroup?.exam?.course : this.mathExercise.course;
    }
}
