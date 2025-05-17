import { Component, TemplateRef, ViewContainerRef, computed, contentChild, inject, input, output } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { facSidebar } from 'app/shared/icons/icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseTitleBarService } from 'app/core/course/shared/services/course-title-bar.service';

/**
 * Shared title bar component used by both course overview and course management components
 */
@Component({
    selector: 'jhi-course-title-bar',
    templateUrl: './course-title-bar.component.html',
    styleUrls: ['./course-title-bar.component.scss'],
    imports: [NgClass, NgStyle, NgbTooltip, FaIconComponent, TranslateDirective, NgTemplateOutlet],
})
export class CourseTitleBarComponent {
    protected readonly facSidebar = facSidebar;
    protected readonly faChevronRight = faChevronRight;
    hasSidebar = input(false);
    isSidebarCollapsed = input(false);
    pageTitle = input('');
    isExamStarted = input(false);

    toggleSidebar = output<void>();
    protected readonly leftContentVcr = contentChild<ViewContainerRef>('[leftContent]');

    courseTitleBarService = inject(CourseTitleBarService);
    readonly hasLeftContentProjection = computed(() => !!this.leftContentVcr());

    get customTitleTpl(): TemplateRef<any> | undefined {
        return this.courseTitleBarService.titleTpl();
    }
    get customActionsTpl(): TemplateRef<any> | undefined {
        return this.courseTitleBarService.actionsTpl();
    }
}
