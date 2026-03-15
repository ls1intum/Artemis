import { Component, OnInit, inject, input, model } from '@angular/core';
import { Router } from '@angular/router';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { AbstractControl, FormControl, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DialogModule } from 'primeng/dialog';

@Component({
    selector: 'jhi-course-unenrollment-modal',
    templateUrl: './course-unenrollment-modal.component.html',
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe, DialogModule],
})
export class CourseUnenrollmentModalComponent implements OnInit {
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private router = inject(Router);

    readonly visible = model<boolean>(false);
    readonly course = input<Course | undefined>();

    confirmationForm: FormGroup;

    // Icons
    faXmark = faXmark;

    ngOnInit(): void {
        this.confirmationForm = new FormGroup({
            confirmationInput: new FormControl('', Validators.compose([Validators.required, this.confirmationInputValidator()])),
        });
    }

    /**
     * Returns true if the student will be able to enroll again, otherwise false.
     */
    get canEnrollAgain() {
        const courseValue = this.course();
        return courseValue?.enrollmentEnabled && dayjs().isBefore(courseValue?.enrollmentEndDate);
    }

    /**
     * Returns true if the input matches the course title, otherwise false.
     */
    get isValidInput() {
        const courseValue = this.course();
        return courseValue && this.confirmationForm && this.confirmationForm.controls[`confirmationInput`].value === courseValue.title;
    }

    /**
     * Method is called when the process is canceled.
     */
    onCancel(): void {
        this.close();
    }

    /**
     * Method is called when the student decides to unenroll.
     */
    onUnenroll(): void {
        this.close();
        const courseValue = this.course();
        this.courseService.unenrollFromCourse(courseValue!.id!).subscribe({
            next: () => {
                this.alertService.success('artemisApp.courseOverview.exerciseList.details.unenrollmentModal.unenrollmentSuccessful');
                this.router.navigate(['/']);
            },
            error: (error: string) => {
                this.alertService.error(error);
            },
        });
    }

    private close(): void {
        this.visible.set(false);
    }

    private confirmationInputValidator(): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            const invalid = !this.isValidInput;
            return invalid ? { invalidInput: { value: control.value } } : null;
        };
    }
}
