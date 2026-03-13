import { Component, inject, input, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faBullseye, faChalkboardTeacher, faChartBar, faClipboard, faCode, faFileAlt, faFileImport, faQuestion, faRocket, faUsers } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { AddExercisePopoverComponent } from 'app/core/course/manage/quick-actions/add-exercise-popover/add-exercise-popover.component';
import { CardWrapperComponent } from 'app/shared/card-wrapper/card-wrapper.component';
import { CourseMaterialImportDialogComponent } from 'app/core/course/manage/course-material-import/course-material-import-dialog.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_LECTURE } from 'app/app.constants';

export enum CourseManagementSection {
    LECTURE = 'lectures',
    EXAM = 'exams',
    FAQ = 'faqs',
    COMPETENCY = 'competency-management',
    TUTORIAL_GROUP = 'tutorial-groups',
}
@Component({
    selector: 'jhi-quick-actions',
    templateUrl: './quick-actions.component.html',
    imports: [
        ButtonComponent,
        UserManagementDropdownComponent,
        TranslateDirective,
        RouterLink,
        NgTemplateOutlet,
        AddExercisePopoverComponent,
        CardWrapperComponent,
        CourseMaterialImportDialogComponent,
    ],
})
export class QuickActionsComponent {
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faCode = faCode;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboard = faClipboard;
    protected readonly faFileAlt = faFileAlt;
    protected readonly faChalkboardTeacher = faChalkboardTeacher;
    protected readonly faQuestion = faQuestion;
    protected readonly faBullseye = faBullseye;
    protected readonly faUsers = faUsers;
    protected readonly faFileImport = faFileImport;
    protected readonly faRocket = faRocket;
    protected readonly CourseManagementSection = CourseManagementSection;
    course = input.required<Course>();
    private router = inject(Router);
    private profileService = inject(ProfileService);

    lectureEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LECTURE);
    atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);

    readonly importDialog = viewChild<CourseMaterialImportDialogComponent>('importDialog');

    navigateToCourseManagementSection(section: CourseManagementSection) {
        const createPath = section === CourseManagementSection.COMPETENCY || section === CourseManagementSection.TUTORIAL_GROUP ? 'create' : 'new';
        return this.router.navigate(['/course-management', this.course().id, section, createPath]);
    }

    openImportDialog(): void {
        this.importDialog()?.open();
    }
}
