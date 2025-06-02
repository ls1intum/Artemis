import { Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faChalkboardUser, faChartBar, faClipboard, faGraduationCap, faListAlt, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { AddExercisePopoverComponent } from 'app/core/course/manage/quick-actions/add-exercise-popover/add-exercise-popover.component';
import { CardWrapperComponent } from 'app/shared/card-wrapper/card-wrapper.component';

export enum CourseManagementSection {
    LECTURE = 'lectures',
    EXAM = 'exams',
    FAQ = 'faqs',
}
@Component({
    selector: 'jhi-quick-actions',
    templateUrl: './quick-actions.component.html',
    imports: [ButtonComponent, UserManagementDropdownComponent, TranslateDirective, RouterLink, NgTemplateOutlet, AddExercisePopoverComponent, CardWrapperComponent],
})
export class QuickActionsComponent {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faListAlt = faListAlt;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboard = faClipboard;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faChalkboardUser = faChalkboardUser;
    protected readonly faQuestion = faQuestion;
    protected readonly CourseManagementSection = CourseManagementSection;
    course = input.required<Course>();
    private router = inject(Router);

    navigateToCourseManagementSection(section: CourseManagementSection) {
        return this.router.navigate(['/course-management', this.course().id, section, 'new']);
    }
}
