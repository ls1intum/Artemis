import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { TranslateService } from '@ngx-translate/core';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { PageTitleView, SidebarView } from 'app/course/shared/sidebar-view.interface';
import { ExamManagementNavigationSidebarComponent } from 'app/exam/manage/exam-management/exam-management-navigation-sidebar/exam-management-navigation-sidebar.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'jhi-exam-management',
    templateUrl: './exam-management.component.html',
    styleUrls: ['./exam-management.component.scss'],
    imports: [ExamManagementNavigationSidebarComponent, CourseSidebarToggleButtonComponent, RouterOutlet, NgTemplateOutlet],
})
export class ExamManagementComponent implements OnInit, OnDestroy, SidebarView, PageTitleView {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private examManagementService = inject(ExamManagementService);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private translateService = inject(TranslateService);
    public courseTitleBarService = inject(CourseTitleBarService);

    readonly documentationType: DocumentationType = 'Exams';

    readonly course = signal<Course>(undefined!);
    readonly exams = signal<Exam[]>(undefined!);

    // SidebarView / PageTitleView requirements
    readonly isCollapsed = signal<boolean>(false);
    readonly pageTitle = signal<string>('');

    readonly headerTitle = signal<string>('');
    private routerSubscription?: Subscription;
    private eventSubscriber?: Subscription;

    toggleSidebar(): void {
        this.isCollapsed.update((state) => !state);
    }

    setPageTitle(pageTitle: string): void {
        this.pageTitle.set(pageTitle);
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
            this.updateHeaderTitle();
        });
        this.updateHeaderTitle();
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

    private updateHeaderTitle(): void {
        let child = this.route.snapshot;
        while (child.firstChild) {
            child = child.firstChild;
        }
        const examId = Number(child.paramMap.get('examId'));
        if (examId) {
            const exam = this.exams()?.find((e) => e.id === examId);
            this.headerTitle.set(exam?.title ?? '');
        } else if (this.router.url.endsWith('/new')) {
            this.headerTitle.set(this.translateService.instant('artemisApp.examManagement.createExam'));
        } else {
            this.headerTitle.set(this.translateService.instant('artemisApp.examManagement.title'));
        }
    }

    /**
     * Load all exams for a course.
     */
    loadAllExamsForCourse() {
        this.examManagementService.findAllExamsForCourse(this.course().id!).subscribe({
            next: (res: HttpResponse<Exam[]>) => {
                this.exams.set(res.body!);
                this.updateHeaderTitle();
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
