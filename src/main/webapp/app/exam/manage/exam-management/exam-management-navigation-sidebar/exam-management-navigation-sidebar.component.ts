import { Component, inject, input, output, signal } from '@angular/core';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { faChevronRight, faClipboard, faEye, faFlaskVial, faInfoCircle, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter } from 'rxjs/operators';
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
    private router = inject(Router);

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

    // State for the accordion
    readonly expandedExams = signal<Set<number>>(new Set<number>());

    constructor() {
        // Automatically expand the exam that matches the current URL
        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            this.expandActiveExam();
        });

        // Check initial URL
        this.expandActiveExam();
    }

    private expandActiveExam() {
        let route = this.router.routerState.root.snapshot;
        let examIdStr: string | null = null;

        // Traverse down the route tree to find the examId param
        while (route) {
            if (route.paramMap.has('examId')) {
                examIdStr = route.paramMap.get('examId');
            }
            route = route.firstChild!;
        }

        if (examIdStr) {
            const examId = parseInt(examIdStr, 10);
            if (!isNaN(examId)) {
                // Expand exam
                this.expandedExams.update((set) => {
                    const newSet = new Set(set);
                    newSet.add(examId);
                    return newSet;
                });

                // Scroll the selected exam into view
                setTimeout(() => {
                    const element = document.getElementById('exam-' + examId);
                    if (element) {
                        element.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    }
                }, 100);
            }
        }
    }

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
