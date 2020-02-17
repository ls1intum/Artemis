import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/alert/alert.service';
import { LectureService } from './lecture.service';
import { CourseService } from 'app/entities/course/course.service';
import { Lecture } from 'app/entities/lecture/lecture.model';
import { EditorMode } from 'app/markdown-editor/markdown-editor.component';
import { Course } from 'app/entities/course/course.model';
import { KatexCommand } from 'app/markdown-editor/commands/katex.command';

@Component({
    selector: 'jhi-lecture-update',
    templateUrl: './lecture-update.component.html',
    styleUrls: ['./lecture-update.component.scss'],
})
export class LectureUpdateComponent implements OnInit {
    EditorMode = EditorMode;
    lecture: Lecture;
    isSaving: boolean;

    courses: Course[];
    startDate: string;
    endDate: string;

    domainCommandsDescription = [new KatexCommand()];

    constructor(
        protected jhiAlertService: AlertService,
        protected lectureService: LectureService,
        protected courseService: CourseService,
        protected activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ lecture }) => {
            this.lecture = lecture;
            this.courseService.find(Number(this.activatedRoute.snapshot.paramMap.get('courseId'))).subscribe((response: HttpResponse<Course>) => {
                this.lecture.course = response.body!;
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
        result.subscribe(
            (res: HttpResponse<Lecture>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(),
        );
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
