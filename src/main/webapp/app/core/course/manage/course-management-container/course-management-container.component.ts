import { AfterViewInit, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, RouterLink, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
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
import { CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { EventManager } from 'app/shared/service/event-manager.service';
import { BaseCourseContainerComponent } from 'app/core/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/core/course/shared/services/sidebar-item.service';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { TutorialGroupsChecklistComponent } from 'app/tutorialgroup/manage/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CompetencyManagementComponent } from 'app/atlas/manage/competency-management/competency-management.component';
import { LearningPathInstructorPageComponent } from 'app/atlas/manage/learning-path-instructor-page/learning-path-instructor-page.component';
import { AssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/assessment-dashboard.component';
import { CourseScoresComponent } from 'app/core/course/manage/course-scores/course-scores.component';
import { FaqComponent } from 'app/communication/faq/faq.component';
import { BuildQueueComponent } from 'app/buildagent/build-queue/build-queue.component';
import { CourseDetailComponent } from 'app/core/course/manage/detail/course-detail.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { CourseManagementExercisesComponent } from 'app/core/course/manage/exercises/course-management-exercises.component';
import { LectureComponent } from 'app/lecture/manage/lecture/lecture.component';
import { CourseManagementStatisticsComponent } from 'app/core/course/manage/statistics/course-management-statistics.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { ButtonSize } from 'app/shared/components/button/button.component';
import { Course, isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
import { CourseDeletionSummaryDTO } from 'app/core/course/shared/entities/course-deletion-summary.model';

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
        RouterLink,
        RouterOutlet,
        NgTemplateOutlet,
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
    private urlSubscription?: Subscription;
    private learningPathsActive = signal(false);
    isOverviewPage = signal(false);

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
    faQuestion = faQuestion;

    protected readonly ButtonSize = ButtonSize;

    async ngOnInit() {
        this.subscription = this.route.firstChild?.params.subscribe((params: { courseId: string }) => {
            const id = Number(params.courseId);
            this.handleCourseIdChange(id);
            this.checkIfOverviewPage();
        });
        this.urlSubscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            this.checkIfOverviewPage();
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

    private checkIfOverviewPage() {
        const currentUrl = this.router.url;
        const isDetailsPage = currentUrl.endsWith(`/${this.courseId()}`) || currentUrl.endsWith(`/${this.courseId()}/`);
        this.isOverviewPage.set(isDetailsPage);
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
        const isInstructor = currentCourse.isAtLeastInstructor;

        sidebarItems.push(...this.sidebarItemService.getManagementDefaultItems(this.courseId()));

        if (currentCourse.isAtLeastEditor) {
            sidebarItems.splice(3, 0, this.sidebarItemService.getLecturesItem(this.courseId()));
        }
        const nonInstructorItems: SidebarItem[] = [];

        const communicationItem = this.addCommunicationsItem(currentCourse);
        const tutorialGroupItem = this.addTutorialGroupsItem(currentCourse, isInstructor);
        this.addAssessmentItem(nonInstructorItems);
        this.addFaqItem(currentCourse, nonInstructorItems);
        nonInstructorItems.unshift(...communicationItem, ...tutorialGroupItem);
        sidebarItems.push(...nonInstructorItems);
        if (isInstructor) {
            const irisItems = this.getIrisSettingsItem();
            const atlasItems = this.getAtlasItems();
            const scoresItem = this.getScoresItem();
            const buildAndLtiItems: SidebarItem[] = [];
            this.addBuildQueueItem(buildAndLtiItems);
            this.addLtiItem(currentCourse, buildAndLtiItems);

            sidebarItems.splice(3, 0, ...irisItems); // After lectures
            sidebarItems.splice(5 + irisItems.length + tutorialGroupItem.length + communicationItem.length, 0, ...atlasItems); // After tutorial groups
            sidebarItems.splice(6 + irisItems.length + tutorialGroupItem.length + communicationItem.length + atlasItems.length, 0, ...scoresItem); // After assessment
            sidebarItems.push(...buildAndLtiItems); // At the end but before settings
            sidebarItems.push(this.sidebarItemService.getCourseSettingsItem(this.courseId()));
        }

        return sidebarItems;
    }

    private addLtiItem(currentCourse: Course, sidebarItems: SidebarItem[]) {
        if (this.ltiEnabled() && currentCourse.onlineCourse) {
            sidebarItems.push(this.sidebarItemService.getLtiConfigurationItem(this.courseId()));
        }
    }

    private addBuildQueueItem(sidebarItems: SidebarItem[]) {
        if (this.localCIActive()) {
            sidebarItems.push(this.sidebarItemService.getBuildQueueItem(this.courseId()));
        }
    }

    private addFaqItem(currentCourse: Course, sidebarItems: SidebarItem[]) {
        if (currentCourse.isAtLeastTutor && currentCourse.faqEnabled) {
            sidebarItems.push(this.sidebarItemService.getFaqManagementItem(this.courseId()));
        }
    }

    private addAssessmentItem(sidebarItems: SidebarItem[]) {
        sidebarItems.push(this.sidebarItemService.getAssessmentDashboardItem(this.courseId()));
    }

    private getScoresItem() {
        return [this.sidebarItemService.getScoresItem(this.courseId())];
    }

    private getAtlasItems() {
        const atlasItems: SidebarItem[] = [];
        if (this.atlasEnabled()) {
            atlasItems.push(this.sidebarItemService.getCompetenciesManagementItem(this.courseId()));
            if (this.learningPathsActive()) {
                atlasItems.push(this.sidebarItemService.getLearningPathManagementItem(this.courseId()));
            }
        }
        return atlasItems;
    }

    private addTutorialGroupsItem(currentCourse: Course, isInstructor = false) {
        const tutorialGroupsItem: SidebarItem[] = [];
        if (currentCourse.tutorialGroupsConfiguration || isInstructor) {
            tutorialGroupsItem.push(this.sidebarItemService.getTutorialGroupsItem(this.courseId()));
        }
        return tutorialGroupsItem;
    }

    private addCommunicationsItem(currentCourse: Course) {
        const communicationItem: SidebarItem[] = [];
        if (isCommunicationEnabled(currentCourse)) {
            communicationItem.push(this.sidebarItemService.getCommunicationsItem(this.courseId()));
        }
        return communicationItem;
    }

    private getIrisSettingsItem() {
        const irisItems: SidebarItem[] = [];
        if (this.irisEnabled()) {
            irisItems.push(this.sidebarItemService.getIrisSettingsItem(this.courseId()));
        }
        return irisItems;
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.eventManager.destroy(this.eventSubscriber);
        this.featureToggleSub?.unsubscribe();
        this.urlSubscription?.unsubscribe();
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
        const courseId = this.course()?.id;
        if (!courseId) {
            return of({});
        }

        return this.courseAdminService.getDeletionSummary(courseId).pipe(map((response) => (response.body ? this.combineSummary(response.body) : {})));
    }

    private combineSummary(summary: CourseDeletionSummaryDTO) {
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
