import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
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

@Component({
    selector: 'jhi-course-unenrollment-modal',
    templateUrl: './course-unenrollment-modal.component.html',
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class CourseUnenrollmentModalComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private router = inject(Router);

    public course: Course;
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
        const enrollmentConfig = this.course?.enrollmentConfiguration;
        return enrollmentConfig?.enrollmentEnabled && dayjs().isBefore(enrollmentConfig?.enrollmentEndDate);
    }

    /**
     * Returns true if the input matches the course title, otherwise false.
     */
    get isValidInput() {
        return this.course && this.confirmationForm && this.confirmationForm.controls[`confirmationInput`].value === this.course.title;
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
        this.courseService.unenrollFromCourse(this.course.id!).subscribe({
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
        this.activeModal.close();
    }

    private confirmationInputValidator(): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            const invalid = !this.isValidInput;
            return invalid ? { invalidInput: { value: control.value } } : null;
        };
    }
}
