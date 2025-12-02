import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { NewCourseRequest } from 'app/core/shared/entities/course-request.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getSemesters } from 'app/shared/util/semester-utils';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { onError } from 'app/shared/util/global.utils';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-course-request',
    templateUrl: './course-request.component.html',
    imports: [ReactiveFormsModule, TranslateDirective, ArtemisTranslatePipe, FormDateTimePickerComponent, ButtonComponent],
})
export class CourseRequestComponent {
    private fb = inject(FormBuilder);
    private courseRequestService = inject(CourseRequestService);
    private alertService = inject(AlertService);

    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly semesters = getSemesters();
    readonly faPaperPlane = faPaperPlane;
    readonly SHORT_NAME_PATTERN = SHORT_NAME_PATTERN;

    isSubmitting = false;
    dateRangeInvalid = false;

    form = this.fb.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        shortName: ['', [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)]],
        semester: [''],
        startDate: [undefined as dayjs.Dayjs | undefined],
        endDate: [undefined as dayjs.Dayjs | undefined],
        testCourse: [false],
        reason: ['', [Validators.required, Validators.minLength(10)]],
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

        const payload: NewCourseRequest = {
            title: this.form.get('title')!.value!,
            shortName: this.form.get('shortName')!.value!,
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
                    shortName: '',
                    semester: '',
                    startDate: undefined,
                    endDate: undefined,
                    testCourse: false,
                    reason: '',
                });
                this.dateRangeInvalid = false;
                this.isSubmitting = false;
            },
            error: (error) => {
                onError(this.alertService, error);
                this.isSubmitting = false;
            },
        });
    }
}
