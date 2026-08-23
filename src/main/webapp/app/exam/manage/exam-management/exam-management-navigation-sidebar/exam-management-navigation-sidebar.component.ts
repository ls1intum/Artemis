import { Component, input, output, signal } from '@angular/core';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { faChevronRight, faClipboard, faEye, faFlaskVial, faHeartBroken, faInfoCircle, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterModule } from '@angular/router';
import { SidebarSubpageItem } from 'app/exam/manage/exam-management/exam-management-navigation-sidebar/sidebar-subpage-item/sidebar-subpage-item';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Course } from 'app/course/shared/entities/course.model';

@Component({
    selector: 'jhi-exam-management-navigation-sidebar',
    templateUrl: './exam-management-navigation-sidebar.component.html',
    styleUrls: ['./exam-management-navigation-sidebar.component.scss'],
    imports: [
        CourseTitleBarTitleComponent,
        CourseSidebarToggleButtonComponent,
        DocumentationButtonComponent,
        NgbCollapse,
        FaIconComponent,
        RouterModule,
        SidebarSubpageItem,
        ArtemisTranslatePipe,
    ],
})
export class ExamManagementNavigationSidebarComponent {
    readonly isCollapsed = input<boolean>(false);
    readonly pageTitle = input<string>('');

    readonly course = input.required<Course>();
    readonly exams = input.required<Exam[]>();

    readonly toggleSidebar = output<void>();

    readonly documentationType: DocumentationType = 'Exams';

    // Icons
    readonly faChevronRight = faChevronRight;
    readonly faListAlt = faListAlt;
    readonly faInfoCircle = faInfoCircle;
    readonly faThList = faThList;
    readonly faWrench = faWrench;
    readonly faUser = faUser;
    readonly faEye = faEye;
    readonly faFlaskVial = faFlaskVial;
    readonly faClipboard = faClipboard;
    readonly faHeartBroken = faHeartBroken;

    // State for the accordion
    readonly expandedExams = signal<Set<number>>(new Set<number>());

    toggleExam(examId: number) {
        this.expandedExams.update((set) => {
            const newSet = new Set(set);
            if (newSet.has(examId)) {
                newSet.delete(examId);
            } else {
                newSet.add(examId);
            }
            return newSet;
        });
    }
}
