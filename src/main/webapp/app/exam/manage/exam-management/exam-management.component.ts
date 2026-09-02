import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { SidebarView } from 'app/course/shared/sidebar-view.interface';
import { ExamManagementNavigationSidebarComponent } from 'app/exam/manage/exam-management/exam-management-navigation-sidebar/exam-management-navigation-sidebar.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.component.scss'],
    imports: [ExamManagementNavigationSidebarComponent, CourseSidebarToggleButtonComponent, RouterOutlet, RouterLink, NgTemplateOutlet, FaIconComponent, ExamModeBadgeComponent],
})
export class ExamManagementComponent implements OnInit, OnDestroy, SidebarView {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private examManagementService = inject(ExamManagementService);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private router = inject(Router);
    public courseTitleBarService = inject(CourseTitleBarService);

    private routerSubscription?: Subscription;
    private eventSubscriber?: Subscription;

    readonly documentationType: DocumentationType = 'Exams';

    readonly faChevronRight = faChevronRight;

    readonly course = signal<Course>(undefined!);
    readonly exams = signal<Exam[]>(undefined!);

    // SidebarView requirements
    readonly isCollapsed = signal<boolean>(false);

    // exam that is currently in view
    readonly currentExam = signal<Exam | undefined>(undefined);

    toggleSidebar(): void {
        this.isCollapsed.update((state) => !state);
    }

    /**
     * Initialize the course and all exams when this view is initialized.
     * Subscribes to 'examListModification' event.
     */
    ngOnInit(): void {
        this.courseService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe({
            next: (res: HttpResponse<Course>) => {
                this.course.set(res.body!);
                this.loadAllExamsForCourse();
                this.registerChangeInExams();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });

        this.routerSubscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            this.updateCurrentExam();
        });
        this.updateCurrentExam();
    }

    /**
     * unsubscribe on component destruction
     */
    ngOnDestroy() {
        if (this.eventSubscriber !== undefined) {
            this.eventManager.destroy(this.eventSubscriber);
        }
        this.routerSubscription?.unsubscribe();
    }

    private updateCurrentExam(): void {
        let child = this.route.snapshot;
        while (child.firstChild) {
            child = child.firstChild;
        }
        const examId = Number(child.paramMap.get('examId'));

        // Do not set the current exam to the source exam when importing
        const isExamImport = this.route.snapshot.firstChild?.routeConfig?.path === 'import/:examId';
        if (examId && !isExamImport) {
            const exam = this.exams()?.find((e) => e.id === examId);
            this.currentExam.set(exam);
        } else {
            this.currentExam.set(undefined);
        }
    }

    /**
     * Load all exams for a course.
     */
    loadAllExamsForCourse() {
        this.examManagementService.findAllExamsForCourse(this.course().id!).subscribe({
            next: (res: HttpResponse<Exam[]>) => {
                this.exams.set(res.body!);
                this.updateCurrentExam();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Subscribes to 'examListModification' events
     */
    registerChangeInExams() {
        this.eventSubscriber = this.eventManager.subscribe('examListModification', () => {
            this.loadAllExamsForCourse();
        });
    }
}
