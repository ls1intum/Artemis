import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { faPaperPlane } from '@fortawesome/free-solid-svg-icons';

import { CourseRequestService } from 'app/course/request/course-request.service';
import { CourseRequestFormComponent } from 'app/course/request/course-request-form.component';
import { BaseCourseRequest } from 'app/course/request/course-request.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { applySemesterToDates, getCurrentAndFutureSemesters, getDefaultSemester, getSemesterDateRange } from 'app/foundation/util/semester-utils';
import { regexValidator } from 'app/shared-ui/form/shortname-validator.directive';
import { onError } from 'app/foundation/util/global.utils';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { SHORT_NAME_PATTERN } from 'app/foundation/constants/input.constants';

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

    readonly isSubmitting = signal(false);
    readonly dateRangeInvalid = signal(false);

    private readonly defaultSemester = getDefaultSemester();
    private readonly defaultRange = getSemesterDateRange(this.defaultSemester);
    private previousSemester: string | undefined = this.defaultSemester;

    form = this.fb.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        shortName: ['', [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)]],
        semester: [this.defaultSemester, [Validators.required]],
        startDate: [this.defaultRange?.startDate, [Validators.required]],
        endDate: [this.defaultRange?.endDate, [Validators.required]],
        testCourse: [false],
        reason: ['', [Validators.required]],
    });

    constructor() {
        this.form.controls.semester.valueChanges.pipe(takeUntilDestroyed()).subscribe((semester) => {
            this.applySemesterDateRange(semester ?? undefined);
        });
    }

    /**
     * Applies the range of the newly selected semester to the two date controls, unless the user picked a date by
     * hand.
     *
     * @param semester the newly selected semester
     */
    private applySemesterDateRange(semester: string | undefined): void {
        const { startDate, endDate } = applySemesterToDates(
            semester,
            this.previousSemester,
            this.form.controls.startDate.value ?? undefined,
            this.form.controls.endDate.value ?? undefined,
        );
        this.previousSemester = semester;
        this.form.controls.startDate.setValue(startDate);
        this.form.controls.endDate.setValue(endDate);
    }

    submit() {
        this.dateRangeInvalid.set(false);
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            return;
        }
        const startDate = this.form.get('startDate')!.value!;
        const endDate = this.form.get('endDate')!.value!;
        if (!startDate.isBefore(endDate)) {
            this.dateRangeInvalid.set(true);
            return;
        }

        const payload: BaseCourseRequest = {
            title: this.form.get('title')!.value!,
            shortName: this.form.get('shortName')!.value!,
            semester: this.form.get('semester')!.value ?? undefined,
            startDate,
            endDate,
            testCourse: this.form.get('testCourse')!.value ?? false,
            reason: this.form.get('reason')!.value!,
        };

        this.isSubmitting.set(true);
        this.courseRequestService.create(payload).subscribe({
            next: () => {
                this.alertService.success('artemisApp.courseRequest.success');
                this.form.reset({
                    title: '',
                    shortName: '',
                    semester: this.defaultSemester,
                    startDate: this.defaultRange?.startDate,
                    endDate: this.defaultRange?.endDate,
                    testCourse: false,
                    reason: '',
                });
                this.previousSemester = this.defaultSemester;
                this.dateRangeInvalid.set(false);
                this.isSubmitting.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.handleSubmitError(error);
                this.isSubmitting.set(false);
            },
        });
    }

    private handleSubmitError(error: HttpErrorResponse): void {
        const errorKey = error.error?.errorKey;
        const isShortNameConflict = errorKey === 'courseShortNameExists' || errorKey === 'courseRequestShortNameExists';

        if (isShortNameConflict) {
            const suggestedShortName = error.error?.params?.suggestedShortName;
            this.alertService.warning('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName: suggestedShortName ?? '' });
            if (suggestedShortName) {
                this.form.patchValue({ shortName: suggestedShortName });
            }
            return;
        }

        onError(this.alertService, error);
    }
}
