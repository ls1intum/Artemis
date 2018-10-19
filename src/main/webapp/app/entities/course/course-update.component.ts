import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';

import { ICourse } from 'app/shared/model/course.model';
import { CourseService } from './course.service';

@Component({
    selector: 'jhi-course-update',
    templateUrl: './course-update.component.html'
})
export class CourseUpdateComponent implements OnInit {
    course: ICourse;
    isSaving: boolean;
    startDate: string;
    endDate: string;

    constructor(private courseService: CourseService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ course }) => {
            this.course = course;
            this.startDate = this.course.startDate != null ? this.course.startDate.format(DATE_TIME_FORMAT) : null;
            this.endDate = this.course.endDate != null ? this.course.endDate.format(DATE_TIME_FORMAT) : null;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        this.course.startDate = this.startDate != null ? moment(this.startDate, DATE_TIME_FORMAT) : null;
        this.course.endDate = this.endDate != null ? moment(this.endDate, DATE_TIME_FORMAT) : null;
        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(this.courseService.update(this.course));
        } else {
            this.subscribeToSaveResponse(this.courseService.create(this.course));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ICourse>>) {
        result.subscribe((res: HttpResponse<ICourse>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
