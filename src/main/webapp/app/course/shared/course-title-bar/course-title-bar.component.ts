import { Component, Signal, TemplateRef, computed, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';

/**
 * Shared title bar component used by both course overview and course management components
 */
@Component({
    selector: 'jhi-course-title-bar',
    templateUrl: './course-title-bar.component.html',
    styleUrls: ['./course-title-bar.component.scss'],
    imports: [NgTemplateOutlet, CourseTitleBarTitleComponent, CourseSidebarToggleButtonComponent],
})
export class CourseTitleBarComponent {
    readonly displayStyle = computed(() => (this.isExamStarted() ? 'none' : 'flex'));
    hasSidebar = input(false);
    isSidebarCollapsed = input(false);
    pageTitle = input('');
    isExamStarted = input(false);
    titleInSidebar = input(false);

    toggleSidebar = output<void>();

    private courseTitleBarService = inject(CourseTitleBarService);
    readonly customTitleTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.courseTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.courseTitleBarService.actionsTemplate());
    readonly hideDefaultTitle = computed(() => this.titleInSidebar() && this.hasSidebar() && !this.isSidebarCollapsed());
}
