import { Component, Signal, TemplateRef, computed, inject, input, output } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { facSidebar } from 'app/shared/icons/icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';

/**
 * Shared title bar component used by both course overview and course management components
 */
@Component({
    selector: 'jhi-course-title-bar',
    templateUrl: './course-title-bar.component.html',
    styleUrls: ['./course-title-bar.component.scss'],
    imports: [NgClass, NgStyle, NgbTooltip, FaIconComponent, NgTemplateOutlet, CourseTitleBarTitleComponent],
})
export class CourseTitleBarComponent {
    protected readonly facSidebar = facSidebar;
    protected readonly faChevronRight = faChevronRight;
    hasSidebar = input(false);
    isSidebarCollapsed = input(false);
    pageTitle = input('');
    isExamStarted = input(false);

    toggleSidebar = output<void>();

    private courseTitleBarService = inject(CourseTitleBarService);
    readonly customTitleTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.courseTitleBarService.titleTemplate());
    readonly customActionsTemplate: Signal<TemplateRef<any> | undefined> = computed(() => this.courseTitleBarService.actionsTemplate());
}
