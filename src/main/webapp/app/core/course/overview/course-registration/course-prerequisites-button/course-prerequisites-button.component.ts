import { Component, input, signal } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CoursePrerequisitesModalComponent } from 'app/core/course/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-prerequisites-button',
    templateUrl: './course-prerequisites-button.component.html',
    imports: [TranslateDirective, CoursePrerequisitesModalComponent],
})
export class CoursePrerequisitesButtonComponent {
    readonly course = input<Course>(undefined!);

    showModal = signal<boolean>(false);

    /**
     * Opens a modal with the prerequisites for the course
     * @param courseId The course id for which to show the prerequisites
     */
    showPrerequisites(courseId: number) {
        this.showModal.set(true);
    }
}
