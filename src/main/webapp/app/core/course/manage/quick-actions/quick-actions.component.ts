import { Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faChalkboardUser, faChartBar, faClipboard, faGraduationCap, faListAlt, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { AddExerciseModalComponent } from 'app/core/course/manage/quick-actions/add-exercise-modal/add-exercise-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';

export enum CourseManagementSection {
    LECTURE = 'lectures',
    EXAM = 'exams',
    FAQ = 'faqs',
}
@Component({
    selector: 'jhi-quick-actions',
    templateUrl: './quick-actions.component.html',
    imports: [ButtonComponent, UserManagementDropdownComponent, TranslateDirective, FaIconComponent, RouterLink, NgTemplateOutlet],
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
    protected readonly CourseManagementSection = CourseManagementSection;
    private router = inject(Router);
    private modalService = inject(NgbModal);
    protected readonly FeatureToggle = FeatureToggle;

    navigateToCourseManagementSection(section: CourseManagementSection) {
        if (!this.course()?.id) {
            return;
        }
        return this.router.navigate(['/course-management', this.course()?.id, section, 'new']);
    }
    openAddExerciseModal() {
        const modalRef = this.modalService.open(AddExerciseModalComponent as Component, { size: 'md' });
        modalRef.componentInstance.course = this.course();
    }
}
