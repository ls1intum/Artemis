import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';

import { IExercise } from 'app/shared/model/exercise.model';
import { ExerciseService } from './exercise.service';
import { ICourse } from 'app/shared/model/course.model';
import { CourseService } from 'app/entities/course';

@Component({
    selector: 'jhi-exercise-update',
    templateUrl: './exercise-update.component.html'
})
export class ExerciseUpdateComponent implements OnInit {
    private _exercise: IExercise;
    isSaving: boolean;

    courses: ICourse[];
    releaseDate: string;
    dueDate: string;

    constructor(
        private jhiAlertService: JhiAlertService,
        private exerciseService: ExerciseService,
        private courseService: CourseService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
        });
        this.courseService.query().subscribe(
            (res: HttpResponse<ICourse[]>) => {
                this.courses = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        this.exercise.releaseDate = moment(this.releaseDate, DATE_TIME_FORMAT);
        this.exercise.dueDate = moment(this.dueDate, DATE_TIME_FORMAT);
        if (this.exercise.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseService.update(this.exercise));
        } else {
            this.subscribeToSaveResponse(this.exerciseService.create(this.exercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IExercise>>) {
        result.subscribe((res: HttpResponse<IExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackCourseById(index: number, item: ICourse) {
        return item.id;
    }
    get exercise() {
        return this._exercise;
    }

    set exercise(exercise: IExercise) {
        this._exercise = exercise;
        this.releaseDate = moment(exercise.releaseDate).format(DATE_TIME_FORMAT);
        this.dueDate = moment(exercise.dueDate).format(DATE_TIME_FORMAT);
    }
}
