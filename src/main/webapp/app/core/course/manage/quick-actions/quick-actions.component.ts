import { Component, inject, input, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faBullseye, faChalkboardTeacher, faCode, faFileAlt, faQuestion, faUsers } from '@fortawesome/free-solid-svg-icons';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { AddExercisePopoverComponent } from 'app/core/course/manage/quick-actions/add-exercise-popover/add-exercise-popover.component';
import { CourseMaterialImportDialogComponent } from 'app/core/course/manage/course-material-import/course-material-import-dialog.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_LECTURE, MODULE_FEATURE_TUTORIALGROUP } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
    styles: [
        `
            :host {
                display: block;
                flex: 1 1 0;
                min-width: 0;
            }

            .quick-actions-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                flex-wrap: wrap;
                gap: 0.75rem;
                margin-bottom: 1.25rem;
            }

            .user-stats {
                display: flex;
                gap: 1.25rem;
            }

            .stat-item {
                text-align: center;
            }

            .stat-label {
                font-size: 0.8rem;
                color: var(--bs-secondary-color);
            }

            .stat-value {
                font-weight: 600;
                font-size: 1.1rem;
            }

            .quick-actions-grid {
                display: flex;
                flex-wrap: wrap;
                gap: 0.75rem;
            }

            .quick-action-card {
                display: flex;
                align-items: center;
                gap: 0.5rem;
                padding: 0.4rem 0.75rem;
                border: 1px solid var(--bs-border-color);
                border-radius: 0.75rem;
                background: var(--overview-card-nested-bg, var(--bs-body-bg));
                cursor: pointer;
                transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
                position: relative;
                overflow: hidden;
                transform: translateY(0);
                will-change: transform;
                width: 180px;

                &::before {
                    content: '';
                    position: absolute;
                    top: 0;
                    left: 0;
                    bottom: 0;
                    width: 3px;
                    background: var(--card-accent, var(--bs-primary));
                    opacity: 0;
                    transition: opacity 0.2s ease;
                }

                &:hover {
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
                    transform: translateY(-1px);
                    border-color: rgba(0, 0, 0, 0);

                    &::before {
                        opacity: 1;
                    }
                }
            }

            .quick-action-card--exercises {
                --card-accent: #6366f1;
            }
            .quick-action-card--lectures {
                --card-accent: #0ea5e9;
            }
            .quick-action-card--tutorial {
                --card-accent: #8b5cf6;
            }
            .quick-action-card--exams {
                --card-accent: #f59e0b;
            }
            .quick-action-card--competencies {
                --card-accent: #10b981;
            }
            .quick-action-card--faqs {
                --card-accent: #ec4899;
            }

            .quick-action-icon {
                width: 28px;
                height: 28px;
                border-radius: 8px;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                flex-shrink: 0;
                color: var(--bs-white);
                background: var(--card-accent, var(--bs-primary));
                box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
                font-size: 0.75rem;
            }

            .quick-action-label {
                font-weight: 600;
                font-size: 0.8rem;
                flex: 1;
                text-align: center;
            }
        `,
    ],
    imports: [UserManagementDropdownComponent, TranslateDirective, RouterLink, NgTemplateOutlet, AddExercisePopoverComponent, CourseMaterialImportDialogComponent, FaIconComponent],
})
export class QuickActionsComponent {
    protected readonly faCode = faCode;
    protected readonly faFileAlt = faFileAlt;
    protected readonly faChalkboardTeacher = faChalkboardTeacher;
    protected readonly faQuestion = faQuestion;
    protected readonly faBullseye = faBullseye;
    protected readonly faUsers = faUsers;
    protected readonly CourseManagementSection = CourseManagementSection;

    course = input.required<Course>();

    private router = inject(Router);
    private profileService = inject(ProfileService);

    lectureEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LECTURE);
    atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
    examEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_EXAM);
    tutorialGroupEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_TUTORIALGROUP);

    readonly importDialog = viewChild<CourseMaterialImportDialogComponent>('importDialog');

    navigateToCourseManagementSection(section: CourseManagementSection) {
        const createPath = section === CourseManagementSection.COMPETENCY || section === CourseManagementSection.TUTORIAL_GROUP ? 'create' : 'new';
        return this.router.navigate(['/course-management', this.course().id, section, createPath]);
    }

    openImportDialog(): void {
        this.importDialog()?.open();
    }
}
