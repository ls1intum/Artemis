import { AfterViewInit, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChartBar, faChevronLeft, faChevronRight, faCircleNotch, faDoorOpen, faEye, faListAlt, faSync, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { CourseNotificationOverviewComponent } from 'app/communication/course-notification/course-notification-overview/course-notification-overview.component';
import { CourseActionItem, CourseSidebarComponent, SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { TeamService } from 'app/exercise/team/team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { BaseCourseContainerComponent } from 'app/core/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/core/course/shared/services/sidebar-item.service';
import { CourseExercisesComponent } from 'app/core/course/overview/course-exercises/course-exercises.component';
import { CourseExamsComponent } from 'app/exam/shared/course-exams/course-exams.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures/course-lectures.component';
import { CourseTutorialGroupsComponent } from 'app/tutorialgroup/shared/course-tutorial-groups/course-tutorial-groups.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { Course, isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
import { CourseUnenrollmentModalComponent } from 'app/core/course/overview/course-unenrollment-modal/course-unenrollment-modal.component';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationInfo } from 'app/communication/shared/entities/course-notification/course-notification-info';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationPresetPickerComponent } from 'app/communication/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['./course-overview.scss', './course-overview.component.scss'],
    imports: [
        NgClass,
        MatSidenavContainer,
        MatSidenavContent,
        MatSidenav,
        RouterLink,
        RouterOutlet,
        NgTemplateOutlet,
        FaIconComponent,
        TranslateDirective,
        CourseNotificationOverviewComponent,
        CourseTitleBarComponent,
        CourseSidebarComponent,
        CourseNotificationPresetPickerComponent,
    ],
    providers: [MetisConversationService],
})
export class CourseOverviewComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private courseExerciseService = inject(CourseExerciseService);
    private teamService = inject(TeamService);
    private websocketService = inject(WebsocketService);
    private serverDateService = inject(ArtemisServerDateService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private examParticipationService = inject(ExamParticipationService);
    private sidebarItemService = inject(CourseSidebarItemService);
    private calendarService = inject(CalendarService);
    protected readonly courseNotificationSettingService: CourseNotificationSettingService = inject(CourseNotificationSettingService);
    protected readonly courseNotificationService: CourseNotificationService = inject(CourseNotificationService);

    private toggleSidebarEventSubscription: Subscription;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;
    private examStartedSubscription: Subscription;
    manageViewLink = signal<string[]>(['']);

    protected selectableSettingPresets: CourseNotificationSettingPreset[];
    protected selectedSettingPreset?: CourseNotificationSettingPreset;
    private info?: CourseNotificationInfo;
    private settingInfo?: CourseNotificationSettingInfo;

    courseActionItems = signal<CourseActionItem[]>([]);
    canUnenroll = signal<boolean>(false);
    showRefreshButton = signal<boolean>(false);
    activatedComponentReference = signal<
        CourseExercisesComponent | CourseLecturesComponent | CourseExamsComponent | CourseTutorialGroupsComponent | CourseConversationsComponent | undefined
    >(undefined);

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faSync = faSync;
    faCircleNotch = faCircleNotch;
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;

    async ngOnInit() {
        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.isSidebarCollapsed.update((value) => this.activatedComponentReference()?.isCollapsed ?? !value);
        });

        this.subscription = this.route?.params.subscribe(async (params: { courseId: string }) => {
            const id = Number(params.courseId);
            this.courseId.set(id);

            this.courseNotificationSettingService.getSettingInfo(this.courseId(), false).subscribe((settingInfo) => {
                if (settingInfo) {
                    this.settingInfo = settingInfo;

                    if (this.info) {
                        this.initializeCourseNotificationValues();
                    }
                }
            });

            this.courseNotificationService.getInfo().subscribe((info) => {
                if (info.body) {
                    this.info = info.body;

                    if (this.settingInfo) {
                        this.initializeCourseNotificationValues();
                    }
                }
            });
        });
        await super.ngOnInit();

        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted: boolean) => {
            this.isExamStarted.set(isStarted);
        });

        this.courseActionItems.set(this.getCourseActionItems());
        this.isSidebarCollapsed.set(this.activatedComponentReference()?.isCollapsed ?? false);
        this.sidebarItems.set(this.getSidebarItems());
        await this.initAfterCourseLoad();
    }

    /**
     * Initializes component values once both settingInfo and info are available.
     * Sets up selectable presets, and the currently selected preset.
     */
    private initializeCourseNotificationValues() {
        this.selectableSettingPresets = this.info!.presets;

        this.selectedSettingPreset =
            this.settingInfo!.selectedPreset === 0 ? undefined : this.selectableSettingPresets.find((preset) => preset.typeId === this.settingInfo!.selectedPreset)!;
    }

    /**
     * Handles selection of a notification preset.
     *
     * @param presetTypeId - The ID of the selected preset (0 for custom settings)
     */
    presetSelected(presetTypeId: number) {
        this.courseNotificationSettingService.setSettingPreset(this.courseId(), presetTypeId, this.selectedSettingPreset);

        this.selectedSettingPreset = presetTypeId === 0 ? undefined : this.selectableSettingPresets.find((preset) => preset.typeId === presetTypeId)!;
    }

    protected handleNavigationEndActions() {
        this.determineManageViewLink();
    }

    handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
    }

    async initAfterCourseLoad() {
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    determineManageViewLink() {
        if (!this.course()) {
            return;
        }

        const courseIdString = this.courseId().toString();
        const routerUrl = this.router.url;
        const baseManagementPath = ['/course-management', courseIdString];
        const routeMappings = [
            { urlPart: 'exams', targetPath: [...baseManagementPath, 'exams'] },
            { urlPart: 'exercises', targetPath: [...baseManagementPath, 'exercises'] },
            { urlPart: 'lectures', targetPath: [...baseManagementPath, 'lectures'], permissionCheck: () => this.course()?.isAtLeastEditor },
            { urlPart: 'communication', targetPath: [...baseManagementPath, 'communication'] },
            { urlPart: 'learning-path', targetPath: [...baseManagementPath, 'learning-paths-management'], permissionCheck: () => this.course()?.isAtLeastInstructor },
            { urlPart: 'competencies', targetPath: [...baseManagementPath, 'competency-management'], permissionCheck: () => this.course()?.isAtLeastInstructor },
            { urlPart: 'faq', targetPath: [...baseManagementPath, 'faqs'] },
            { urlPart: 'statistics', targetPath: [...baseManagementPath, 'course-statistics'] },
            {
                urlPart: 'tutorial-groups',
                targetPath: [...baseManagementPath, 'tutorial-groups-checklist'],
                permissionCheck: () => this.course()?.isAtLeastInstructor || this.course()?.tutorialGroupsConfiguration,
            },
        ];

        const matchedRoute = routeMappings.find((route) => {
            return routerUrl.includes(route.urlPart) && (!route.permissionCheck || route.permissionCheck());
        });

        this.manageViewLink.set(matchedRoute ? matchedRoute.targetPath : baseManagementPath);
    }

    /**
     * Fetch the course from the server including all exercises, lectures, exams and competencies
     */
    loadCourse(refresh = false): Observable<void> {
        this.refreshingCourse.set(refresh);
        const observable = this.courseManagementService.findOneForDashboard(this.courseId()).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course.set(res.body);
                }

                setTimeout(() => this.refreshingCourse.set(false), 500); // ensure min animation duration
            }),
            // catch 403 errors where registration is possible
            catchError((error: HttpErrorResponse) => {
                if (error.status === 403) {
                    this.redirectToCourseRegistrationPageIfCanRegisterOrElseThrow(error);
                    // Emit a default value, for example `undefined`
                    return of(undefined);
                } else {
                    return throwError(() => error);
                }
            }),
            // handle other errors
            catchError((error: HttpErrorResponse) => {
                const errorMessage = error.headers.get('X-artemisApp-message')!;
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
                return throwError(() => error);
            }),
        );
        // Start fetching, even if we don't subscribe to the result.
        // This enables just calling this method to refresh the course, without subscribing to it:
        this.loadCourseSubscription?.unsubscribe();
        if (refresh) {
            this.loadCourseSubscription = observable.subscribe();
            this.calendarService.reloadEvents();
        }
        return observable;
    }

    protected getHasSidebar(): boolean {
        return !!this.route.snapshot.firstChild?.data?.hasSidebar;
    }

    protected handleComponentActivation(componentRef: any): void {
        if (
            componentRef instanceof CourseExercisesComponent ||
            componentRef instanceof CourseLecturesComponent ||
            componentRef instanceof CourseTutorialGroupsComponent ||
            componentRef instanceof CourseExamsComponent ||
            componentRef instanceof CourseConversationsComponent
        ) {
            this.activatedComponentReference.set(componentRef);
        }

        this.isSidebarCollapsed.update((value) => value ?? false);
        this.getShowRefreshButton();
    }

    handleToggleSidebar(): void {
        if (!this.activatedComponentReference()) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference();
        childRouteComponent?.toggleSidebar();
        this.isSidebarCollapsed.set(childRouteComponent!.isCollapsed);
    }

    getShowRefreshButton(): void {
        this.showRefreshButton.set(this.route.snapshot.firstChild?.data?.showRefreshButton ?? false);
    }

    getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const currentCourse = this.course();

        // Use the service to get sidebar items
        const defaultItems = this.sidebarItemService.getStudentDefaultItems(
            currentCourse?.studentCourseAnalyticsDashboardEnabled || currentCourse?.irisCourseChatEnabled,
            currentCourse?.trainingEnabled,
        );
        sidebarItems.push(...defaultItems);

        if (currentCourse?.lectures) {
            const lecturesItem = this.sidebarItemService.getLecturesItem();
            sidebarItems.splice(-2, 0, lecturesItem);
        }

        if (currentCourse?.exams && this.hasVisibleExams()) {
            const examsItem = this.sidebarItemService.getExamsItem();
            sidebarItems.unshift(examsItem);
        }

        if (isCommunicationEnabled(currentCourse)) {
            const communicationsItem = this.sidebarItemService.getCommunicationsItem();
            sidebarItems.push(communicationsItem);
        }

        if (this.hasTutorialGroups()) {
            const tutorialGroupsItem = this.sidebarItemService.getTutorialGroupsItem();
            sidebarItems.push(tutorialGroupsItem);
        }

        if (this.atlasEnabled && this.hasCompetencies()) {
            const competenciesItem = this.sidebarItemService.getCompetenciesItem();
            sidebarItems.push(competenciesItem);

            if (currentCourse?.learningPathsEnabled) {
                const learningPathItem = this.sidebarItemService.getLearningPathItem();
                sidebarItems.push(learningPathItem);
            }
        }

        if (currentCourse?.faqEnabled) {
            const faqItem = this.sidebarItemService.getFaqItem();
            sidebarItems.push(faqItem);
        }

        sidebarItems.push(this.sidebarItemService.getNotificationSettingsItem());

        return sidebarItems;
    }

    getCourseActionItems(): CourseActionItem[] {
        const courseActionItems = [];
        const currentCourse = this.course();

        this.canUnenroll.set(this.canStudentUnenroll() && !currentCourse?.isAtLeastTutor);
        if (this.canUnenroll()) {
            const unenrollItem = this.getUnenrollItem();
            courseActionItems.push(unenrollItem);
        }
        return courseActionItems;
    }

    canStudentUnenroll(): boolean {
        const currentCourse = this.course();
        return !!currentCourse?.unenrollmentEnabled && dayjs().isBefore(currentCourse?.unenrollmentEndDate);
    }

    courseActionItemClick(item?: CourseActionItem) {
        if (item?.action) {
            item.action(item);
        }
    }

    getUnenrollItem(): CourseActionItem {
        return {
            title: 'Unenroll',
            icon: faDoorOpen,
            translation: 'artemisApp.courseOverview.exerciseList.details.unenrollmentButton',
            action: () => this.openUnenrollStudentModal(),
        };
    }

    openUnenrollStudentModal() {
        const modalRef = this.modalService.open(CourseUnenrollmentModalComponent, { size: 'xl' });
        modalRef.componentInstance.course = this.course();
    }

    /**
     * Determines whether the user can register for the course by trying to fetch the for-registration version
     */
    canRegisterForCourse(): Observable<boolean> {
        return this.courseManagementService.findOneForRegistration(this.courseId()).pipe(
            map(() => true),
            catchError((error: HttpErrorResponse) => {
                if (error.status === 403) {
                    return of(false);
                } else {
                    return throwError(() => error);
                }
            }),
        );
    }

    redirectToCourseRegistrationPage() {
        this.router.navigate(['courses', this.courseId(), 'register']);
    }

    redirectToCourseRegistrationPageIfCanRegisterOrElseThrow(error: Error): void {
        this.canRegisterForCourse().subscribe((canRegister) => {
            if (canRegister) {
                this.redirectToCourseRegistrationPage();
            } else {
                throw error;
            }
        });
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        const currentCourse = this.course();
        if (currentCourse?.exams) {
            for (const exam of currentCourse.exams) {
                if (exam.visibleDate && dayjs(exam.visibleDate).isBefore(this.serverDateService.now())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the course has any competencies or prerequisites
     */
    hasCompetencies(): boolean {
        const currentCourse = this.course();
        return !!(currentCourse?.numberOfCompetencies || currentCourse?.numberOfPrerequisites);
    }

    /**
     * Check if the course has a tutorial groups
     */
    hasTutorialGroups(): boolean {
        return !!this.course()?.numberOfTutorialGroups;
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        const teamAssignmentUpdates = await this.teamService.teamAssignmentUpdates;
        this.teamAssignmentUpdateListener = teamAssignmentUpdates.subscribe((teamAssignment: TeamAssignmentPayload) => {
            const currentCourse = this.course();
            const exercise = currentCourse?.exercises?.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
            if (exercise) {
                exercise.studentAssignedTeamId = teamAssignment.teamId;
                exercise.studentParticipations = teamAssignment.studentParticipations;
            }
        });
    }

    subscribeForQuizChanges() {
        // subscribe to quizzes which get visible
        if (!this.quizExercisesChannel) {
            this.quizExercisesChannel = '/topic/courses/' + this.courseId() + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.websocketService.subscribe(this.quizExercisesChannel);
            this.websocketService.receive(this.quizExercisesChannel)?.subscribe((quizExercise: QuizExercise) => {
                quizExercise = this.courseExerciseService.convertExerciseDatesFromServer(quizExercise);
                // the quiz was set to visible or started, we should add it to the exercise list and display it at the top
                const currentCourse = this.course();
                if (currentCourse && currentCourse.exercises) {
                    currentCourse.exercises = currentCourse.exercises.filter((exercise) => exercise.id !== quizExercise.id);
                    currentCourse.exercises.push(quizExercise);
                    this.course.set(currentCourse);
                }
            });
        }
    }

    /** Navigate to a new Course */
    switchCourse(course: Course) {
        const url = ['courses', course.id, 'dashboard'];
        this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
            this.router.navigate(url);
        });
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.websocketService.unsubscribe(this.quizExercisesChannel);
        }
        this.examStartedSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
    }
}
