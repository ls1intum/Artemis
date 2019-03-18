import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';
import { LectureService } from './lecture.service';
import { Course, CourseService } from 'app/entities/course';
import { Lecture } from 'app/entities/lecture/lecture.model';

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html'
})
export class LectureUpdateComponent implements OnInit {
    lecture: Lecture;
    isSaving: boolean;

    courses: Course[];
    startDate: string;
    endDate: string;

    constructor(
        protected jhiAlertService: JhiAlertService,
        protected lectureService: LectureService,
        protected courseService: CourseService,
        protected activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.startDate = this.lecture.startDate != null ? this.lecture.startDate.format(DATE_TIME_FORMAT) : null;
            this.endDate = this.lecture.endDate != null ? this.lecture.endDate.format(DATE_TIME_FORMAT) : null;
        });
        this.courseService
            .query()
            .pipe(
                filter((mayBeOk: HttpResponse<Course[]>) => mayBeOk.ok),
                map((response: HttpResponse<Course[]>) => response.body)
            )
            .subscribe((res: Course[]) => (this.courses = res), (res: HttpErrorResponse) => this.onError(res.message));
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        this.lecture.startDate = this.startDate != null ? moment(this.startDate, DATE_TIME_FORMAT) : null;
        this.lecture.endDate = this.endDate != null ? moment(this.endDate, DATE_TIME_FORMAT) : null;
        if (this.lecture.id !== undefined) {
            this.subscribeToSaveResponse(this.lectureService.update(this.lecture));
        } else {
            this.subscribeToSaveResponse(this.lectureService.create(this.lecture));
        }
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<Lecture>>) {
        result.subscribe((res: HttpResponse<Lecture>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}
