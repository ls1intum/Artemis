import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';
import { LectureService } from './lecture.service';
import { Course, CourseService } from 'app/entities/course';
import { Lecture } from 'app/entities/lecture/lecture.model';

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
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
        protected activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.courseService.find(Number(this.activatedRoute.snapshot.paramMap.get('courseId'))).subscribe((response: HttpResponse<Course>) => {
                this.lecture.course = response.body;
            });
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
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
