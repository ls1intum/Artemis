import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { CourseRequestFormComponent } from 'app/core/course/request/course-request-form.component';
import { BaseCourseRequest } from 'app/core/shared/entities/course-request.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { getCurrentAndFutureSemesters, getDefaultSemester } from 'app/shared/util/semester-utils';
import { onError } from 'app/shared/util/global.utils';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-course-request',
    templateUrl: './course-request.component.html',
    imports: [ReactiveFormsModule, TranslateDirective, CourseRequestFormComponent, ButtonComponent],
})
export class CourseRequestComponent {
    private fb = inject(FormBuilder);
    private courseRequestService = inject(CourseRequestService);
    private alertService = inject(AlertService);

    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly semesters = getCurrentAndFutureSemesters();
    protected readonly faPaperPlane = faPaperPlane;

    isSubmitting = false;
    dateRangeInvalid = false;

    form = this.fb.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        semester: [getDefaultSemester(), [Validators.required]],
        startDate: [undefined as dayjs.Dayjs | undefined],
        endDate: [undefined as dayjs.Dayjs | undefined],
        testCourse: [false],
        reason: ['', [Validators.required]],
    });

    submit() {
        this.dateRangeInvalid = false;
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            return;
        }
        const startDate = this.form.get('startDate')!.value ?? undefined;
        const endDate = this.form.get('endDate')!.value ?? undefined;
        if (startDate && endDate && !startDate.isBefore(endDate)) {
            this.dateRangeInvalid = true;
            return;
        }

        const payload: BaseCourseRequest = {
            title: this.form.get('title')!.value!,
            semester: this.form.get('semester')!.value ?? undefined,
            startDate,
            endDate,
            testCourse: this.form.get('testCourse')!.value ?? false,
            reason: this.form.get('reason')!.value!,
        };

        this.isSubmitting = true;
        this.courseRequestService.create(payload).subscribe({
            next: () => {
                this.alertService.success('artemisApp.courseRequest.success');
                this.form.reset({
                    title: '',
                    semester: getDefaultSemester(),
                    startDate: undefined,
                    endDate: undefined,
                    testCourse: false,
                    reason: '',
                });
                this.dateRangeInvalid = false;
                this.isSubmitting = false;
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
                this.isSubmitting = false;
            },
        });
    }
}
