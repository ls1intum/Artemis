import { Component, input, output } from '@angular/core';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-exam-management-navigation-sidebar',
    templateUrl: './exam-management-navigation-sidebar.component.html',
    styleUrls: ['./exam-management-navigation-sidebar.component.scss'],
    imports: [CourseTitleBarTitleComponent, CourseSidebarToggleButtonComponent, DocumentationButtonComponent],
})
export class ExamManagementNavigationSidebarComponent {
    readonly isCollapsed = input<boolean>(false);
    readonly pageTitle = input<string>('');

    readonly toggleSidebar = output<void>();

    readonly documentationType: DocumentationType = 'Exams';
}
