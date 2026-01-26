import { AfterViewInit, Component, DestroyRef, ElementRef, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, RouterLink, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, of } from 'rxjs';
import { distinctUntilChanged, filter, map, startWith } from 'rxjs/operators';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { WebsocketService } from 'app/shared/service/websocket.service';

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
    faTrash,
    faUndo,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { EventManager } from 'app/shared/service/event-manager.service';
import { BaseCourseContainerComponent } from 'app/core/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/core/course/shared/services/sidebar-item.service';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ActionType, EntitySummaryCategory } from 'app/shared/delete-dialog/delete-dialog.model';
import { IrisSettingsUpdateComponent } from 'app/iris/manage/settings/iris-settings-update/iris-settings-update.component';
import { TutorialGroupsChecklistComponent } from 'app/tutorialgroup/manage/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CompetencyManagementComponent } from 'app/atlas/manage/competency-management/competency-management.component';
import { LearningPathInstructorPageComponent } from 'app/atlas/manage/learning-path-instructor-page/learning-path-instructor-page.component';
import { AssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/assessment-dashboard.component';
import { CourseScoresComponent } from 'app/core/course/manage/course-scores/course-scores.component';
import { FaqComponent } from 'app/communication/faq/faq.component';
import { BuildOverviewComponent } from 'app/buildagent/build-queue/build-overview.component';
import { CourseDetailComponent } from 'app/core/course/manage/detail/course-detail.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { CourseManagementExercisesComponent } from 'app/core/course/manage/exercises/course-management-exercises.component';
import { LectureComponent } from 'app/lecture/manage/lecture/lecture.component';
import { CourseManagementStatisticsComponent } from 'app/core/course/manage/statistics/course-management-statistics.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { Course, isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
import { CourseSummaryDTO } from 'app/core/course/shared/entities/course-summary.model';
import { CourseOperationProgressDTO, CourseOperationType } from 'app/core/course/shared/entities/course-operation-progress.model';
import { CourseOperationProgressComponent } from 'app/core/course/manage/course-operation-progress/course-operation-progress.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { toSignal } from '@angular/core/rxjs-interop';
import { IS_AT_LEAST_ADMIN } from 'app/shared/constants/authority.constants';
import { Subscription } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';

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
        FaIconComponent,
        CourseOperationProgressComponent,
        ArtemisTranslatePipe,
    ],
})
export class CourseManagementContainerComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private readonly destroyRef = inject(DestroyRef);
    private readonly eventManager = inject(EventManager);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly sidebarItemService = inject(CourseSidebarItemService);
    private readonly courseAdminService = inject(CourseAdminService);
    private readonly websocketService = inject(WebsocketService);

    protected readonly faTimes = faTimes;
    protected readonly faEye = faEye;
    protected readonly faWrench = faWrench;
    protected readonly faTable = faTable;
    protected readonly faFlag = faFlag;
    protected readonly faListAlt = faListAlt;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboard = faClipboard;
    protected readonly faSync = faSync;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faQuestion = faQuestion;
    protected readonly faTrash = faTrash;
    protected readonly faUndo = faUndo;

    protected readonly ButtonSize = ButtonSize;
    protected readonly ActionType = ActionType;
    protected readonly IS_AT_LEAST_ADMIN = IS_AT_LEAST_ADMIN;

    // Keep progressSubscription as it needs manual management due to dynamic re-subscription
    private progressSubscription?: Subscription;
    // Keep eventSubscriber as EventManager.subscribe() returns Subscription, not Observable
    private eventSubscriber?: Subscription;

    operationProgress = signal<CourseOperationProgressDTO | undefined>(undefined);

    private learningPathsActive = signal(false);
    courseBody = viewChild<ElementRef<HTMLElement>>('courseBodyContainer');
    isSettingsPage = signal(false);
    studentViewLink = signal<string[]>([]);

    // Stream of finalized URLs (after redirects), seeded with the current URL for reloads
    private readonly finalizedUrl$ = this.router.events.pipe(
        filter((routerEvent): routerEvent is NavigationEnd => routerEvent instanceof NavigationEnd),
        map((navigationEndEvent) => navigationEndEvent.urlAfterRedirects ?? navigationEndEvent.url),
        startWith(this.router.url),
        distinctUntilChanged(),
    );

    readonly removePadding = toSignal(this.finalizedUrl$.pipe(map((currentUrl) => currentUrl.includes('test-runs') && currentUrl.includes('conduction'))), {
        initialValue: this.router.url.includes('test-runs') && this.router.url.includes('conduction'),
    });

    // we cannot use signals here because the child component doesn't expect it
    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activatedComponentReference = signal<
        | CourseDetailComponent
        | ExamManagementComponent
        | CourseManagementExercisesComponent
        | LectureComponent
        | CourseManagementStatisticsComponent
        | IrisSettingsUpdateComponent
        | CourseConversationsComponent
        | TutorialGroupsChecklistComponent
        | CompetencyManagementComponent
        | LearningPathInstructorPageComponent
        | AssessmentDashboardComponent
        | CourseScoresComponent
        | FaqComponent
        | BuildOverviewComponent
        | undefined
    >(undefined);

    async ngOnInit() {
        this.route.firstChild?.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params: { courseId: string }) => {
            const id = Number(params.courseId);
            this.handleCourseIdChange(id);
            this.checkIfSettingsPage();
        });

        this.featureToggleService
            .getFeatureToggleActive(FeatureToggle.LearningPaths)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((isActive) => {
                this.learningPathsActive.set(isActive);
            });

        await super.ngOnInit();

        // Subscribe to course modifications and reload the course after a change.
        this.eventSubscriber = this.eventManager.subscribe('courseModification', () => {
            this.subscribeToCourseUpdates(this.courseId()!);
        });
    }

    protected handleNavigationEndActions(): void {
        this.checkIfSettingsPage();
        this.determineStudentViewLink();
    }

    private checkIfSettingsPage() {
        const currentUrl = this.router.url;
        const isSettingsPage = currentUrl.endsWith(`/course-management/${this.courseId()}/settings`);
        this.isSettingsPage.set(isSettingsPage);
    }

    handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
        this.determineStudentViewLink();
        this.subscribeToCourseUpdates(courseId);
        this.subscribeToOperationProgress(courseId);
    }

    private subscribeToOperationProgress(courseId: number) {
        // Unsubscribe from previous subscription if any
        this.progressSubscription?.unsubscribe();

        const topic = `/topic/courses/${courseId}/operation-progress`;
        this.progressSubscription = this.websocketService.subscribe<CourseOperationProgressDTO>(topic).subscribe((progress) => {
            if (progress.startedAt) {
                progress.startedAt = convertDateFromServer(progress.startedAt);
            }
            this.operationProgress.set(progress);
        });
    }

    closeProgress() {
        const progress = this.operationProgress();
        this.operationProgress.set(undefined);
        // Navigate to course list after closing a completed delete operation
        if (progress?.operationType === CourseOperationType.DELETE) {
            this.router.navigate(['/course-management']);
        }
    }

    determineStudentViewLink() {
        const courseIdString = this.courseId().toString();
        const routerUrl = this.router.url;
        const baseStudentPath = ['/courses', courseIdString];

        const routeMappings = [
            { urlPart: 'exams', targetPath: [...baseStudentPath, 'exams'] },
            { urlPart: 'exercises', targetPath: [...baseStudentPath, 'exercises'] },
            { urlPart: 'lectures', targetPath: [...baseStudentPath, 'lectures'] },
            { urlPart: 'communication', targetPath: [...baseStudentPath, 'communication'] },
            { urlPart: 'learning-path-management', targetPath: [...baseStudentPath, 'learning-path'] },
            { urlPart: 'competency-management', targetPath: [...baseStudentPath, 'competencies'] },
            { urlPart: 'faqs', targetPath: [...baseStudentPath, 'faq'] },
            {
                urlPart: ['tutorial-groups', 'tutorial-groups-checklist'],
                targetPath: [...baseStudentPath, 'tutorial-groups'],
                matcher: (url: string | string[], parts: string[]) => parts.some((part) => url.includes(part)),
            },
            { urlPart: 'course-statistics', targetPath: [...baseStudentPath, 'statistics'] },
        ];

        const defaultPath = [...baseStudentPath, 'dashboard'];

        const matchedRoute = routeMappings.find((route) => {
            if (route.matcher) {
                return route.matcher(routerUrl, Array.isArray(route.urlPart) ? route.urlPart : [route.urlPart]);
            }
            return routerUrl.includes(route.urlPart);
        });

        this.studentViewLink.set(matchedRoute ? matchedRoute.targetPath : defaultPath);
    }

    private subscribeToCourseUpdates(courseId: number) {
        this.courseManagementService.find(courseId).subscribe((courseResponse) => {
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
            componentRef instanceof BuildOverviewComponent
        ) {
            this.activatedComponentReference.set(componentRef);
        }
        if (this.activatedComponentReference() instanceof CourseConversationsComponent) {
            const childRouteComponent = this.activatedComponentReference() as CourseConversationsComponent;
            this.isSidebarCollapsed.set(childRouteComponent?.isCollapsed ?? false);
        }
        // if we don't scroll to the top, the page will be scrolled to the last position which is not expected by the user
        if (this.courseBody()) {
            this.courseBody()!.nativeElement.scrollTop = 0;
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

        if (this.lectureEnabled && currentCourse.isAtLeastEditor) {
            sidebarItems.splice(3, 0, this.sidebarItemService.getLecturesItem(this.courseId()));
        }
        const nonInstructorItems: SidebarItem[] = [];

        const communicationItem = this.addCommunicationItem(currentCourse);
        const tutorialGroupItem = this.addTutorialGroupsItem(currentCourse, isInstructor);
        this.addAssessmentItem(nonInstructorItems);
        this.addFaqItem(currentCourse, nonInstructorItems);
        nonInstructorItems.unshift(...communicationItem, ...tutorialGroupItem);
        sidebarItems.push(...nonInstructorItems);

        // Atlas items are available for editors and instructors
        const atlasItems = currentCourse.isAtLeastEditor ? this.getAtlasItems() : [];

        if (isInstructor) {
            const irisItems = this.getIrisSettingsItem();
            const scoresItem = this.getScoresItem();
            const buildAndLtiItems: SidebarItem[] = [];
            this.addBuildQueueItem(buildAndLtiItems);
            this.addLtiItem(currentCourse, buildAndLtiItems);

            sidebarItems.splice(3, 0, ...irisItems); // After lectures
            sidebarItems.splice(5 + irisItems.length + tutorialGroupItem.length + communicationItem.length, 0, ...atlasItems); // After tutorial groups
            sidebarItems.splice(6 + irisItems.length + tutorialGroupItem.length + communicationItem.length + atlasItems.length, 0, ...scoresItem); // After assessment
            sidebarItems.push(...buildAndLtiItems); // At the end but before settings
            sidebarItems.push(this.sidebarItemService.getCourseSettingsItem(this.courseId()));
        } else if (currentCourse.isAtLeastEditor) {
            // For editors (non-instructors), add Atlas items after tutorial groups
            sidebarItems.splice(3 + tutorialGroupItem.length + communicationItem.length, 0, ...atlasItems);
        }

        return sidebarItems;
    }

    private addLtiItem(currentCourse: Course, sidebarItems: SidebarItem[]) {
        if (this.ltiEnabled && currentCourse.onlineCourse) {
            sidebarItems.push(this.sidebarItemService.getLtiConfigurationItem(this.courseId()));
        }
    }

    private addBuildQueueItem(sidebarItems: SidebarItem[]) {
        if (this.localCIActive) {
            sidebarItems.push(this.sidebarItemService.getBuildQueueItem(this.courseId()));
        }
    }

    private addFaqItem(currentCourse: Course, sidebarItems: SidebarItem[]) {
        if (currentCourse.isAtLeastInstructor || currentCourse.faqEnabled) {
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
        if (this.atlasEnabled) {
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

    private addCommunicationItem(currentCourse: Course) {
        const communicationItem: SidebarItem[] = [];
        if (currentCourse.isAtLeastInstructor || isCommunicationEnabled(currentCourse)) {
            communicationItem.push(this.sidebarItemService.getCommunicationsItem(this.courseId()));
        }
        return communicationItem;
    }

    private getIrisSettingsItem() {
        const irisItems: SidebarItem[] = [];
        if (this.irisEnabled) {
            irisItems.push(this.sidebarItemService.getIrisSettingsItem(this.courseId()));
        }
        return irisItems;
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.progressSubscription?.unsubscribe();
        this.eventSubscriber?.unsubscribe();
    }

    fetchCourseDeletionSummary(): Observable<EntitySummaryCategory[]> {
        const courseId = this.course()?.id;
        if (!courseId) {
            return of([]);
        }

        return this.courseAdminService.getCourseSummary(courseId).pipe(map((response) => (response.body ? this.buildDeletionSummaryCategories(response.body) : [])));
    }

    private buildDeletionSummaryCategories(summary: CourseSummaryDTO): EntitySummaryCategory[] {
        const categories: EntitySummaryCategory[] = [];
        const prefix = 'artemisApp.course.delete.summary';

        // Users
        categories.push({
            titleKey: `${prefix}.category.users`,
            items: [
                { labelKey: `${prefix}.numberOfStudents`, value: summary.numberOfStudents },
                { labelKey: `${prefix}.numberOfTutors`, value: summary.numberOfTutors },
                { labelKey: `${prefix}.numberOfEditors`, value: summary.numberOfEditors },
                { labelKey: `${prefix}.numberOfInstructors`, value: summary.numberOfInstructors },
            ],
        });

        // Course Structure (delete-only: these are permanently deleted)
        categories.push({
            titleKey: `${prefix}.category.courseStructure`,
            items: [
                { labelKey: `${prefix}.numberOfExams`, value: summary.numberOfExams },
                { labelKey: `${prefix}.numberOfLectures`, value: summary.numberOfLectures },
                { labelKey: `${prefix}.numberOfCompetencies`, value: summary.numberOfCompetencies },
                { labelKey: `${prefix}.numberOfFaqs`, value: summary.numberOfFaqs },
                { labelKey: `${prefix}.numberOfTutorialGroups`, value: summary.numberOfTutorialGroups },
            ],
        });

        // Exercises (delete-only: breakdown by type)
        categories.push({
            titleKey: `${prefix}.category.exercises`,
            items: [
                { labelKey: `${prefix}.numberOfProgrammingExercises`, value: summary.numberOfProgrammingExercises },
                { labelKey: `${prefix}.numberOfTextExercises`, value: summary.numberOfTextExercises },
                { labelKey: `${prefix}.numberOfModelingExercises`, value: summary.numberOfModelingExercises },
                { labelKey: `${prefix}.numberOfQuizExercises`, value: summary.numberOfQuizExercises },
                { labelKey: `${prefix}.numberOfFileUploadExercises`, value: summary.numberOfFileUploadExercises },
            ],
        });

        // Student Work
        categories.push({
            titleKey: `${prefix}.category.studentWork`,
            items: [
                { labelKey: `${prefix}.numberOfParticipations`, value: summary.numberOfParticipations },
                { labelKey: `${prefix}.numberOfSubmissions`, value: summary.numberOfSubmissions },
                { labelKey: `${prefix}.numberOfResults`, value: summary.numberOfResults },
            ],
        });

        // Communication
        categories.push({
            titleKey: `${prefix}.category.communication`,
            items: [
                { labelKey: `${prefix}.numberOfConversations`, value: summary.numberOfConversations },
                { labelKey: `${prefix}.numberOfPosts`, value: summary.numberOfPosts },
                { labelKey: `${prefix}.numberOfReplies`, value: summary.numberOfAnswerPosts },
            ],
        });

        // Learning Data
        categories.push({
            titleKey: `${prefix}.category.learningData`,
            items: [
                { labelKey: `${prefix}.numberOfCompetencyProgress`, value: summary.numberOfCompetencyProgress },
                { labelKey: `${prefix}.numberOfLearnerProfiles`, value: summary.numberOfLearnerProfiles },
            ],
        });

        // AI Data
        categories.push({
            titleKey: `${prefix}.category.aiData`,
            items: [
                { labelKey: `${prefix}.numberOfIrisChatSessions`, value: summary.numberOfIrisChatSessions },
                { labelKey: `${prefix}.numberOfLLMTraces`, value: summary.numberOfLLMTraces },
            ],
        });

        // Infrastructure
        categories.push({
            titleKey: `${prefix}.category.infrastructure`,
            items: [{ labelKey: `${prefix}.numberOfBuilds`, value: summary.numberOfBuilds }],
        });

        return categories;
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
                // Don't navigate immediately - let the progress overlay show
                // Navigation will happen when user closes the progress overlay
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    fetchCourseResetSummary(): Observable<EntitySummaryCategory[]> {
        const courseId = this.course()?.id;
        if (!courseId) {
            return of([]);
        }

        return this.courseAdminService.getCourseSummary(courseId).pipe(map((response) => (response.body ? this.buildResetSummaryCategories(response.body) : [])));
    }

    private buildResetSummaryCategories(summary: CourseSummaryDTO): EntitySummaryCategory[] {
        const prefix = 'artemisApp.course.reset.summary';

        return [
            // Users (to be removed - instructors stay)
            {
                titleKey: `${prefix}.category.users`,
                items: [
                    { labelKey: `${prefix}.numberOfStudents`, value: summary.numberOfStudents },
                    { labelKey: `${prefix}.numberOfTutors`, value: summary.numberOfTutors },
                    { labelKey: `${prefix}.numberOfEditors`, value: summary.numberOfEditors },
                ],
            },
            // Student Work (reset-only: participations, submissions, and results)
            {
                titleKey: `${prefix}.category.studentWork`,
                items: [
                    { labelKey: `${prefix}.numberOfParticipations`, value: summary.numberOfParticipations },
                    { labelKey: `${prefix}.numberOfSubmissions`, value: summary.numberOfSubmissions },
                    { labelKey: `${prefix}.numberOfResults`, value: summary.numberOfResults },
                ],
            },
            // Communication
            {
                titleKey: `${prefix}.category.communication`,
                items: [
                    { labelKey: `${prefix}.numberOfConversations`, value: summary.numberOfConversations },
                    { labelKey: `${prefix}.numberOfPosts`, value: summary.numberOfPosts },
                    { labelKey: `${prefix}.numberOfReplies`, value: summary.numberOfAnswerPosts },
                ],
            },
            // Learning Data
            {
                titleKey: `${prefix}.category.learningData`,
                items: [
                    { labelKey: `${prefix}.numberOfCompetencyProgress`, value: summary.numberOfCompetencyProgress },
                    { labelKey: `${prefix}.numberOfLearnerProfiles`, value: summary.numberOfLearnerProfiles },
                ],
            },
            // AI Data
            {
                titleKey: `${prefix}.category.aiData`,
                items: [
                    { labelKey: `${prefix}.numberOfIrisChatSessions`, value: summary.numberOfIrisChatSessions },
                    { labelKey: `${prefix}.numberOfLLMTraces`, value: summary.numberOfLLMTraces },
                ],
            },
            // Infrastructure
            {
                titleKey: `${prefix}.category.infrastructure`,
                items: [{ labelKey: `${prefix}.numberOfBuilds`, value: summary.numberOfBuilds }],
            },
            // Items to Reset (reset-only: structure preserved but data cleared)
            {
                titleKey: `${prefix}.category.itemsToReset`,
                items: [
                    { labelKey: `${prefix}.numberOfExercises`, value: summary.numberOfExercises },
                    { labelKey: `${prefix}.numberOfExams`, value: summary.numberOfExams },
                ],
            },
        ];
    }

    /**
     * Resets the course by removing all student data while preserving the course structure
     * @param courseId id of the course that will be reset
     */
    resetCourse(courseId: number) {
        this.courseAdminService.reset(courseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'courseModification',
                    content: 'artemisApp.course.reset',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
