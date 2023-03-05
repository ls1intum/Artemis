import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-prerequisites-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-course-registration-prerequisites-button',
    templateUrl: './course-registration-prerequisites-button.component.html',
})
export class CourseRegistrationPrerequisitesButtonComponent {
    @Input() course: Course;

    constructor(private modalService: NgbModal) {}

    /**
     * Opens a modal with the prerequisites for the course
     * @param courseId The course id for which to show the prerequisites
     */
    showPrerequisites(courseId: number) {
        const modalRef = this.modalService.open(CoursePrerequisitesModalComponent, { size: 'xl' });
        modalRef.componentInstance.courseId = courseId;
    }
}
