import { Component, Input, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-course-prerequisites-button',
    templateUrl: './course-prerequisites-button.component.html',
})
export class CoursePrerequisitesButtonComponent {
    private modalService = inject(NgbModal);

    @Input() course: Course;

    /**
     * Opens a modal with the prerequisites for the course
     * @param courseId The course id for which to show the prerequisites
     */
    showPrerequisites(courseId: number) {
        const modalRef = this.modalService.open(CoursePrerequisitesModalComponent, { size: 'xl' });
        modalRef.componentInstance.courseId = courseId;
    }
}
