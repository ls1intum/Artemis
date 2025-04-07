import { AfterViewInit, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import {
    faChartBar,
    faChevronLeft,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faEye,
    faFlag,
    faListAlt,
    faQuestion,
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';

import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { ButtonSize } from 'app/shared/components/button.component';
import { CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { EventManager } from 'app/shared/service/event-manager.service';
import { Course, isCommunicationEnabled } from 'app/core/shared/entities/course.model';
import { facSidebar } from 'app/shared/icons/icons';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations.component';
import { BaseCourseContainerComponent } from 'app/core/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/core/course/shared/sidebar-item.service';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseAdminService } from 'app/core/course/manage/course-admin.service';
import { CourseManagementExercisesComponent } from 'app/core/course/manage/course-management-exercises.component';
import { LectureComponent } from 'app/lecture/manage/lecture.component';
import { CourseManagementStatisticsComponent } from 'app/core/course/manage/course-management-statistics.component';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { TutorialGroupsChecklistComponent } from 'app/tutorialgroup/manage/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CompetencyManagementComponent } from 'app/atlas/manage/competency-management/competency-management.component';
import { LearningPathInstructorPageComponent } from 'app/atlas/manage/learning-path-instructor-page/learning-path-instructor-page.component';
import { AssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/assessment-dashboard.component';
import { CourseScoresComponent } from 'app/core/course/manage/course-scores/course-scores.component';
import { FaqComponent } from 'app/communication/faq/faq.component';
import { BuildQueueComponent } from 'app/buildagent/build-queue/build-queue.component';
import { CourseDetailComponent } from 'app/core/course/manage/detail/course-detail.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';

@Component({
    selector: 'jhi-course-management-container',
    templateUrl: './course-management-container.component.html',
    styleUrls: ['course-management-container.component.scss'],
    providers: [MetisConversationService],
    imports: [
        NgClass,
        MatSidenavContainer,
        MatSidenavContent,
        MatSidenav,
        NgbTooltip,
        NgStyle,
        RouterLink,
        RouterOutlet,
        NgTemplateOutlet,
        FaIconComponent,
        TranslateDirective,
        CourseSidebarComponent,
        CourseExamArchiveButtonComponent,
        CourseTitleBarComponent,
        DeleteButtonDirective,
        HasAnyAuthorityDirective,
    ],
})
export class CourseManagementContainerComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private eventManager = inject(EventManager);
    private featureToggleService = inject(FeatureToggleService);
    private sidebarItemService = inject(CourseSidebarItemService);
    private courseAdminService = inject(CourseAdminService);

    private eventSubscriber: Subscription;
    private featureToggleSub: Subscription;
    private courseSub?: Subscription;
    private learningPathsActive = signal(false);

    // we cannot use signals here because the child component doesn't expect it
    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activatedComponentReference = signal<
        | CourseDetailComponent
        | ExamManagementComponent
        | CourseManagementExercisesComponent
        | LectureComponent
        | CourseManagementStatisticsComponent
        | IrisCourseSettingsUpdateComponent
        | CourseConversationsComponent
        | TutorialGroupsChecklistComponent
        | CompetencyManagementComponent
        | LearningPathInstructorPageComponent
        | AssessmentDashboardComponent
        | CourseScoresComponent
        | FaqComponent
        | BuildQueueComponent
        | undefined
    >(undefined);

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faFlag = faFlag;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faClipboard = faClipboard;
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    facSidebar = facSidebar;
    faQuestion = faQuestion;

    protected readonly ButtonSize = ButtonSize;

    async ngOnInit() {
        this.subscription = this.route.firstChild?.params.subscribe((params: { courseId: string }) => {
            const id = Number(params.courseId);
            this.handleCourseIdChange(id);
        });

        this.featureToggleSub = this.featureToggleService.getFeatureToggleActive(FeatureToggle.LearningPaths).subscribe((isActive) => {
            this.learningPathsActive.set(isActive);
        });
        await super.ngOnInit();

        // Subscribe to course modifications and reload the course after a change.
        this.eventSubscriber = this.eventManager.subscribe('courseModification', () => {
            this.subscribeToCourseUpdates(this.courseId()!);
        });
    }

    handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
        this.subscribeToCourseUpdates(courseId);
    }

    private subscribeToCourseUpdates(courseId: number) {
        this.courseSub?.unsubscribe();
        this.courseSub = this.courseManagementService.find(courseId).subscribe((courseResponse) => {
            if (courseResponse.body) {
                this.course.set(courseResponse.body!);
            }
            this.sidebarItems.set(this.getSidebarItems());
            this.updateRecentlyAccessedCourses().catch();
        });
    }

    loadCourse(): Observable<void> {
        return this.courseManagementService.findOneForDashboard(this.courseId()!).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course.set(res.body);
                }
                this.setupConversationService();
            }),
        );
    }

    protected getHasSidebar(): boolean {
        return this.communicationRouteLoaded();
    }

    /** Navigate to a new Course */
    switchCourse(course: Course) {
        const url = ['course-management', course.id];
        this.router.navigate(url).then(() => {
            this.handleCourseIdChange(course.id!);
        });
    }

    protected handleComponentActivation(componentRef: any): void {
        if (
            componentRef instanceof CourseDetailComponent ||
            componentRef instanceof CourseManagementExercisesComponent ||
            componentRef instanceof ExamManagementComponent ||
            componentRef instanceof LectureComponent ||
            componentRef instanceof CourseManagementStatisticsComponent ||
            componentRef instanceof CourseConversationsComponent ||
            componentRef instanceof TutorialGroupsChecklistComponent ||
            componentRef instanceof CompetencyManagementComponent ||
            componentRef instanceof LearningPathInstructorPageComponent ||
            componentRef instanceof AssessmentDashboardComponent ||
            componentRef instanceof CourseScoresComponent ||
            componentRef instanceof FaqComponent ||
            componentRef instanceof BuildQueueComponent
        ) {
            this.activatedComponentReference.set(componentRef);
        }
        if (this.activatedComponentReference() instanceof CourseConversationsComponent) {
            const childRouteComponent = this.activatedComponentReference() as CourseConversationsComponent;
            this.isSidebarCollapsed.set(childRouteComponent?.isCollapsed ?? false);
        }
    }

    handleToggleSidebar(): void {
        if (!this.activatedComponentReference() || !(this.activatedComponentReference() instanceof CourseConversationsComponent)) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference() as CourseConversationsComponent;
        childRouteComponent.toggleSidebar();
        this.isSidebarCollapsed.set(childRouteComponent.isCollapsed);
    }

    override getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const currentCourse = this.course();

        if (!currentCourse) {
            return [];
        }

        sidebarItems.push(...this.sidebarItemService.getManagementDefaultItems(this.courseId()));
        if (currentCourse.isAtLeastEditor) {
            sidebarItems.splice(3, 0, this.sidebarItemService.getLecturesItem(this.courseId()));
        }

        if (currentCourse.isAtLeastInstructor && this.irisEnabled()) {
            sidebarItems.push(this.sidebarItemService.getIrisSettingsItem(this.courseId()));
        }

        if (currentCourse && isCommunicationEnabled(currentCourse)) {
            sidebarItems.push(this.sidebarItemService.getCommunicationsItem(this.courseId()));
        }

        if (currentCourse.tutorialGroupsConfiguration || currentCourse.isAtLeastInstructor) {
            sidebarItems.push(this.sidebarItemService.getTutorialGroupsItem(this.courseId()));
        }

        if (currentCourse.isAtLeastInstructor && this.atlasEnabled()) {
            sidebarItems.push(this.sidebarItemService.getCompetenciesManagementItem(this.courseId()));
        }

        if (currentCourse.isAtLeastInstructor && this.atlasEnabled() && this.learningPathsActive()) {
            sidebarItems.push(this.sidebarItemService.getLearningPathManagementItem(this.courseId()));
        }

        sidebarItems.push(this.sidebarItemService.getAssessmentDashboardItem(this.courseId()));

        if (currentCourse.isAtLeastInstructor) {
            sidebarItems.push(this.sidebarItemService.getScoresItem(this.courseId()));
        }

        if (currentCourse.isAtLeastTutor && currentCourse.faqEnabled) {
            sidebarItems.push(this.sidebarItemService.getFaqManagementItem(this.courseId()));
        }

        if (currentCourse.isAtLeastInstructor && this.localCIActive()) {
            sidebarItems.push(this.sidebarItemService.getBuildQueueItem(this.courseId()));
        }
        if (this.ltiEnabled() && currentCourse.onlineCourse && currentCourse.isAtLeastInstructor) {
            sidebarItems.push(this.sidebarItemService.getLtiConfigurationItem(this.courseId()));
        }

        return sidebarItems;
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.eventManager.destroy(this.eventSubscriber);
        this.featureToggleSub?.unsubscribe();
        this.courseSub?.unsubscribe();
    }

    private getExistingSummaryEntries(): EntitySummary {
        const numberOfExercisesPerType = new Map<ExerciseType, number>();
        this.course()?.exercises?.forEach((exercise) => {
            if (exercise.type === undefined) {
                return;
            }
            const oldValue = numberOfExercisesPerType.get(exercise.type) ?? 0;
            numberOfExercisesPerType.set(exercise.type, oldValue + 1);
        });

        const numberStudents = this.course()?.numberOfStudents ?? 0;
        const numberTutors = this.course()?.numberOfTeachingAssistants ?? 0;
        const numberEditors = this.course()?.numberOfEditors ?? 0;
        const numberInstructors = this.course()?.numberOfInstructors ?? 0;
        const isTestCourse = this.course()?.testCourse;

        return {
            'artemisApp.course.delete.summary.numberStudents': numberStudents,
            'artemisApp.course.delete.summary.numberTutors': numberTutors,
            'artemisApp.course.delete.summary.numberEditors': numberEditors,
            'artemisApp.course.delete.summary.numberInstructors': numberInstructors,
            'artemisApp.course.delete.summary.isTestCourse': isTestCourse,
        };
    }

    fetchCourseDeletionSummary(): Observable<EntitySummary> {
        if (this.course()?.id === undefined) {
            return of({});
        }

        return this.courseAdminService.getDeletionSummary(this.course()!.id!).pipe(
            map((response) => {
                const summary = response.body;

                if (summary === null) {
                    return {};
                }

                return {
                    ...this.getExistingSummaryEntries(),
                    'artemisApp.course.delete.summary.numberExams': summary.numberExams,
                    'artemisApp.course.delete.summary.numberLectures': summary.numberLectures,
                    'artemisApp.course.delete.summary.numberProgrammingExercises': summary.numberProgrammingExercises,
                    'artemisApp.course.delete.summary.numberTextExercises': summary.numberTextExercises,
                    'artemisApp.course.delete.summary.numberFileUploadExercises': summary.numberFileUploadExercises,
                    'artemisApp.course.delete.summary.numberQuizExercises': summary.numberQuizExercises,
                    'artemisApp.course.delete.summary.numberModelingExercises': summary.numberModelingExercises,
                    'artemisApp.course.delete.summary.numberBuilds': summary.numberOfBuilds,
                    'artemisApp.course.delete.summary.numberCommunicationPosts': summary.numberOfCommunicationPosts,
                    'artemisApp.course.delete.summary.numberAnswerPosts': summary.numberOfAnswerPosts,
                };
            }),
        );
    }

    /**
     * Deletes the course
     * @param courseId id the course that will be deleted
     */
    deleteCourse(courseId: number) {
        this.courseAdminService.delete(courseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'courseListModification',
                    content: 'artemisApp.course.deleted',
                });
                this.dialogErrorSource.next('');
                this.router.navigate(['/course-management']);
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
