import { ActivatedRoute } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Observable } from 'rxjs';

import { regexValidator } from 'app/shared/form/shortname-validator.directive';

import { Course } from './course.model';
import { CourseService } from './course.service';

@Component({
    selector: 'jhi-course-update',
    templateUrl: './course-update.component.html'
})
export class CourseUpdateComponent implements OnInit {
    courseForm: FormGroup;
    course: Course;
    isSaving: boolean;

    shortNamePattern = /^[a-zA-Z][a-zA-Z0-9]*$/; // must start with a letter and cannot contain special characters
    errorMessages = {
        shortName: {
            minlength: 'Short Name must have a length of 3 or more characters.',
            forbidden: 'Short Name must start with a letter and cannot contain special characters.'
        }
    };

    constructor(private courseService: CourseService, private activatedRoute: ActivatedRoute, private jhiAlertService: JhiAlertService) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ course }) => {
            this.course = course;
        });
        this.courseForm = new FormGroup({
            id: new FormControl(this.course.id),
            title: new FormControl(this.course.title, [Validators.required]),
            shortName: new FormControl(this.course.shortName, {
                validators: [Validators.required, Validators.minLength(3), regexValidator(this.shortNamePattern)],
                updateOn: 'blur'
            }),
            studentGroupName: new FormControl(this.course.studentGroupName, [Validators.required]),
            teachingAssistantGroupName: new FormControl(this.course.teachingAssistantGroupName, [Validators.required]),
            instructorGroupName: new FormControl(this.course.instructorGroupName, [Validators.required]),
            startDate: new FormControl(this.course.startDate),
            endDate: new FormControl(this.course.endDate),
            onlineCourse: new FormControl(this.course.onlineCourse)
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.course.id !== undefined) {
            this.subscribeToSaveResponse(this.courseService.update(this.courseForm.value));
        } else {
            this.subscribeToSaveResponse(this.courseService.create(this.courseForm.value));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Course>>) {
        result.subscribe((res: HttpResponse<Course>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError(res));
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-arTeMiSApp-alert');
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
    }

    get shortName() {
        return this.courseForm.get('shortName');
    }
}
