import dayjs from 'dayjs/esm';
import { Subscription, takeWhile } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';

import { Course } from 'app/entities/course.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { MathExercise } from 'app/entities/math-exercise.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { MathExerciseService } from 'app/exercises/math/manage/math-exercise.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';

@Component({
    selector: 'jhi-math-exercise-compose',
    templateUrl: './math-exercise-compose.component.html',
})
export class MathExerciseComposeComponent implements OnInit, OnDestroy {
    readonly documentationType: DocumentationType = 'Math';

    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    mathExercise: MathExercise;
    course: Course | undefined;
    isExamExercise: boolean;

    doughnutStats: ExerciseManagementStatisticsDto;

    private eventSubscription: Subscription;

    private componentActive = true;

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];

    constructor(
        private eventManager: EventManager,
        private mathExerciseService: MathExerciseService,
        private route: ActivatedRoute,
        private statisticsService: StatisticsService,
    ) {}

    /**
     * Loads the math exercise and subscribes to changes of it on component initialization.
     */
    ngOnInit() {
        this.route.data.pipe(takeWhile(() => this.componentActive)).subscribe((data) => this.initExercise(data.mathExercise));
        this.registerChangeInMathExercises();
    }

    private initExercise(exercise: MathExercise) {
        this.mathExercise = exercise;
        this.isExamExercise = !!this.mathExercise.exerciseGroup;
        this.course = this.isExamExercise ? this.mathExercise.exerciseGroup?.exam?.course : this.mathExercise.course;
    }

    /**
     * Requests the math exercise referenced by the given exerciseId.
     * @param exerciseId of the math exercise of type {number}
     */
    private load(exerciseId: number) {
        this.mathExerciseService.find(exerciseId).subscribe((response: HttpResponse<MathExercise>) => this.initExercise(response.body!));
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    /**
     * Unsubscribe from changes of math exercise on destruction of component.
     */
    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscription);
        this.componentActive = false;
    }

    /**
     * Subscribe to changes of the math exercise.
     */
    registerChangeInMathExercises() {
        this.eventSubscription = this.eventManager.subscribe('mathExerciseListModification', () => this.load(this.mathExercise.id!));
    }

    protected readonly EditorMode = EditorMode;
}
