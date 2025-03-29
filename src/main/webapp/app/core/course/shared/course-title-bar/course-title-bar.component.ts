import { Component, input, output } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { facSidebar } from 'app/shared/icons/icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Shared title bar component used by both course overview and course management components
 */
@Component({
    selector: 'jhi-course-title-bar',
    templateUrl: './course-title-bar.component.html',
    styleUrls: ['./course-title-bar.component.scss'],
    imports: [NgClass, NgStyle, NgbTooltip, FaIconComponent, TranslateDirective],
})
export class CourseTitleBarComponent {
    hasSidebar = input(false);
    isSidebarCollapsed = input(false);
    pageTitle = input('');
    titleTranslation = input('');
    isExamStarted = input(false);

    toggleSidebar = output<void>();

    // Icons
    facSidebar = facSidebar;
    faChevronRight = faChevronRight;
}
