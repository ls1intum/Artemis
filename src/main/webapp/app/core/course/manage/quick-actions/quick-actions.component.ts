import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faChalkboardUser, faChartBar, faClipboard, faGraduationCap, faListAlt, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { AddExerciseModalComponent } from 'app/core/course/manage/quick-actions/add-exercise-modal/add-exercise-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-quick-actions',
    templateUrl: './quick-actions.component.html',
    styleUrls: ['./quick-actions.component.scss'],
    imports: [ButtonComponent, UserManagementDropdownComponent],
})
export class QuickActionsComponent {
    course = input<Course | undefined>();
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faListAlt = faListAlt;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboard = faClipboard;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faChalkboardUser = faChalkboardUser;
    protected readonly faQuestion = faQuestion;
    private router = inject(Router);
    private modalService = inject(NgbModal);

    linkToLectureCreation() {
        if (!this.course()?.id) {
            return;
        }
        this.router.navigate(['course-management', this.course()?.id, 'lectures', 'new']);
    }
    linkToExamCreation() {
        if (!this.course()?.id) {
            return;
        }
        this.router.navigate(['course-management', this.course()?.id, 'exams', 'new']);
    }
    linkToFaqCreation() {
        if (!this.course()?.id) {
            return;
        }
        this.router.navigate(['course-management', this.course()?.id, 'faqs', 'new']);
    }
    openAddExerciseModal() {
        const modalRef = this.modalService.open(AddExerciseModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.course = this.course();
    }
}
