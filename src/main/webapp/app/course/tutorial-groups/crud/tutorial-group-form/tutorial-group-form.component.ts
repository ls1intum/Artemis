import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseGroup } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { map, finalize, Observable, catchError, of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';

export interface TutorialGroupFormData {
    title?: string;
    teachingAssistant?: User;
}

// user with label for ng-select
export class UserWithLabel extends User {
    label: string;
}

const nonWhitespaceRegExp: RegExp = new RegExp('\\S');

@Component({
    selector: 'jhi-tutorial-group-form',
    templateUrl: './tutorial-group-form.component.html',
})
export class TutorialGroupFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupFormData = {
        title: undefined,
        teachingAssistant: undefined,
    };

    @Input() courseId: number;
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();

    form: FormGroup;
    teachingAssistantsAreLoading = false;
    teachingAssistants$: Observable<User[]>;

    constructor(private fb: FormBuilder, private courseManagementService: CourseManagementService, private alertService: AlertService) {}

    get titleControl() {
        return this.form.get('title');
    }

    get teachingAssistantControl() {
        return this.form.get('teachingAssistant');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    ngOnInit(): void {
        this.teachingAssistants$ = this.getTeachingAssistantsInCourse();
        this.initializeForm();
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    submitForm() {
        const tutorialGroupFormData: TutorialGroupFormData = { ...this.form.value };
        this.formSubmitted.emit(tutorialGroupFormData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            title: [undefined, [Validators.required, Validators.maxLength(255), Validators.pattern(nonWhitespaceRegExp)]],
            teachingAssistant: [undefined, [Validators.required]],
        });
    }

    private setFormValues(formData: TutorialGroupFormData) {
        if (formData.teachingAssistant) {
            formData.teachingAssistant = this.createUserWithLabel(formData.teachingAssistant);
        }
        this.form.patchValue(formData);
    }

    private createUserWithLabel(user: User): UserWithLabel {
        return { ...user, label: this.createUserLabel(user) };
    }

    private createUserLabel(ta: User) {
        let label = '';
        if (ta.firstName) {
            label += ta.firstName + ' ';
        }
        if (ta.lastName) {
            label += ta.lastName + ' ';
        }
        if (ta.login) {
            label += '(' + ta.login + ')';
        }
        return label.trim();
    }

    private getTeachingAssistantsInCourse() {
        return this.courseManagementService.getAllUsersInCourseGroup(this.courseId, CourseGroup.TUTORS).pipe(
            catchError((res: HttpErrorResponse) => {
                onError(this.alertService, res);
                return of([]);
            }),
            map((res: HttpResponse<User[]>) => res.body!),
            map((user: User[]) => {
                return user.map((u) => this.createUserWithLabel(u));
            }),
            finalize(() => {
                this.teachingAssistantsAreLoading = false;
            }),
        );
    }
}
