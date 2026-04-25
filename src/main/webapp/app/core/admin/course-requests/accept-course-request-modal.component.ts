import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import dayjs from 'dayjs/esm';

import { CourseRequest, CourseRequestAccept, InstructorCourse } from 'app/core/shared/entities/course-request.model';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { getCurrentAndFutureSemesters } from 'app/shared/util/semester-utils';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-accept-course-request-modal',
    templateUrl: './accept-course-request-modal.component.html',
    imports: [DialogModule, ButtonModule, ReactiveFormsModule, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, FormDateTimePickerComponent],
})
export class AcceptCourseRequestModalComponent {
    private readonly fb = inject(FormBuilder);
    private readonly courseRequestService = inject(CourseRequestService);
    private readonly alertService = inject(AlertService);

    protected readonly semesters = getCurrentAndFutureSemesters();

    readonly visible = signal(false);
    readonly isSubmitting = signal(false);
    readonly dateRangeInvalid = signal(false);
    readonly instructorCourses = signal<InstructorCourse[]>([]);
    readonly loadingCourses = signal(false);

    private currentRequest: CourseRequest | undefined;
    private onSuccess?: (updated: CourseRequest) => void;

    readonly acceptForm = this.fb.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        shortName: ['', [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)]],
        semester: [''],
        startDate: [undefined as dayjs.Dayjs | undefined],
        endDate: [undefined as dayjs.Dayjs | undefined],
    });

    open(request: CourseRequest, onSuccess: (updated: CourseRequest) => void): void {
        this.currentRequest = request;
        this.onSuccess = onSuccess;
        this.dateRangeInvalid.set(false);
        this.isSubmitting.set(false);
        this.instructorCourses.set([]);

        this.acceptForm.reset({
            title: request.title,
            shortName: '',
            semester: request.semester ?? '',
            startDate: request.startDate,
            endDate: request.endDate,
        });

        this.visible.set(true);
        this.loadInstructorCourses(request);
    }

    cancel(): void {
        this.visible.set(false);
    }

    submit(): void {
        this.dateRangeInvalid.set(false);
        if (this.acceptForm.invalid || !this.currentRequest?.id) {
            this.acceptForm.markAllAsTouched();
            return;
        }

        const startDate = this.acceptForm.get('startDate')!.value;
        const endDate = this.acceptForm.get('endDate')!.value;
        if (startDate && endDate && !startDate.isBefore(endDate)) {
            this.dateRangeInvalid.set(true);
            return;
        }

        const acceptData: CourseRequestAccept = {
            title: this.acceptForm.get('title')!.value!,
            shortName: this.acceptForm.get('shortName')!.value!,
            semester: this.acceptForm.get('semester')!.value ?? undefined,
            startDate: startDate ?? undefined,
            endDate: endDate ?? undefined,
        };

        this.isSubmitting.set(true);
        this.courseRequestService.acceptRequest(this.currentRequest.id, acceptData).subscribe({
            next: (updated) => {
                this.alertService.success('artemisApp.courseRequest.admin.acceptSuccess', { title: updated.title });
                this.visible.set(false);
                this.isSubmitting.set(false);
                this.onSuccess?.(updated);
            },
            error: (error: HttpErrorResponse) => {
                this.handleAcceptError(error);
                this.isSubmitting.set(false);
            },
        });
    }

    private handleAcceptError(error: HttpErrorResponse): void {
        const errorKey = error.error?.errorKey;
        if (errorKey === 'courseShortNameExists') {
            this.alertService.warning('artemisApp.courseRequest.admin.shortNameConflictModal');
            return;
        }
        onError(this.alertService, error);
    }

    private loadInstructorCourses(request: CourseRequest): void {
        if (!request.requester?.id) {
            return;
        }
        this.loadingCourses.set(true);
        this.courseRequestService.getInstructorCourses(request.requester.id).subscribe({
            next: (courses) => {
                this.instructorCourses.set(courses);
                this.loadingCourses.set(false);
            },
            error: () => {
                this.loadingCourses.set(false);
            },
        });
    }
}
