import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';
@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html'
})
export class ProgrammingExerciseComponent extends ExerciseComponent implements OnInit, OnDestroy {
    @Input() programmingExercises: ProgrammingExercise[];

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseExerciseService: CourseExerciseService,
        courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        eventManager: JhiEventManager,
        route: ActivatedRoute
    ) {
        super(courseService, route, eventManager);
        this.programmingExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body;
                // reconnect exercise with course
                this.programmingExercises.forEach(programmingExercise => {
                    programmingExercise.course = this.course;
                });
                this.emitExerciseCount(this.programmingExercises.length);
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    trackId(index: number, item: ProgrammingExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'programmingExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
