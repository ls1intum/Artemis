import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { Course, CourseService } from 'app/entities/course';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html'
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    programmingExercise: ProgrammingExercise;
    isSaving: boolean;
    maxScorePattern = '^[1-9]{1}[0-9]{0,4}$'; // make sure max score is a positive natural integer and not too large
    courses: Course[];

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
        });
        this.activatedRoute.params.subscribe(params => {
            if (params['courseId']) {
                const courseId = params['courseId'];
                this.courseService.find(courseId).subscribe(res => {
                    const course = res.body;
                    this.programmingExercise.course = course;
                });
            }
        });
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.programmingExercise.id !== undefined) {
            // TODO how can we support edit in this case? some attributes cannot be easily changed
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.automaticSetup(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            (res: HttpResponse<ProgrammingExercise>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res)
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}
