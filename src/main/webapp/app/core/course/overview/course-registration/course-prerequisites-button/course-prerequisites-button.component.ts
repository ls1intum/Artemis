import { Component, inject, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CoursePrerequisitesModalComponent } from 'app/core/course/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-prerequisites-button',
    templateUrl: './course-prerequisites-button.component.html',
    imports: [TranslateDirective],
})
export class CoursePrerequisitesButtonComponent {
    private modalService = inject(NgbModal);

    readonly course = input<Course>(undefined!);

    /**
     * Opens a modal with the prerequisites for the course
     * @param courseId The course id for which to show the prerequisites
     */
    showPrerequisites(courseId: number) {
        const modalRef = this.modalService.open(CoursePrerequisitesModalComponent, { size: 'xl' });
        modalRef.componentInstance.courseId = courseId;
    }
}
