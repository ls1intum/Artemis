import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-course-unenrollment-modal',
    templateUrl: './course-unenrollment-modal.component.html',
})
export class CourseUnenrollmentModalComponent {
    public course: Course;

    // Icons
    faXmark = faXmark;

    constructor(private activeModal: NgbActiveModal, private courseService: CourseManagementService, private alertService: AlertService, private router: Router) {}

    /**
     * Returns true if the student will be able to enroll again, otherwise false.
     */
    get canEnrollAgain() {
        return this.course.enrollmentEnabled && dayjs().isBefore(this.course.enrollmentEndDate);
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
}
