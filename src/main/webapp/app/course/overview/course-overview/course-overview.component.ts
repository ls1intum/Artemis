import { AfterViewInit, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Params, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChartBar, faChevronLeft, faChevronRight, faCircleNotch, faDoorOpen, faEye, faListAlt, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { CourseActionItem, CourseSidebarComponent, SidebarItem } from 'app/course/shared/course-sidebar/course-sidebar.component';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { TeamService } from 'app/exercise/team/team.service';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { BaseCourseContainerComponent } from 'app/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/course/shared/services/sidebar-item.service';
import { CourseExercisesComponent } from 'app/course/overview/course-exercises/course-exercises.component';
import { CourseExamsComponent } from 'app/exam/shared/course-exams/course-exams.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures/course-lectures.component';
import { CourseTutorialGroupsComponent } from 'app/tutorialgroup/overview/course-tutorial-groups/course-tutorial-groups.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { CourseUnenrollmentModalComponent } from 'app/course/overview/course-unenrollment-modal/course-unenrollment-modal.component';
import { CourseTitleBarComponent } from 'app/course/shared/course-title-bar/course-title-bar.component';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { CourseIrisComponent } from 'app/iris/overview/course-iris/course-iris.component';

/**
 * Reads the collapsed state from a route-activated component that may expose `isCollapsed` either as a
 * method or as a boolean property. Returns undefined if the component does not provide the information.
 */
function readComponentCollapsed(componentRef: unknown): boolean | undefined {
    if (typeof componentRef !== 'object' || !componentRef) {
        return undefined;
    }
    const isCollapsed = (componentRef as { isCollapsed?: (() => boolean) | boolean }).isCollapsed;
    return typeof isCollapsed === 'function' ? isCollapsed.call(componentRef) : isCollapsed;
}

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['./course-overview.scss', './course-overview.component.scss'],
    imports: [CdkScrollable, NgClass, RouterOutlet, NgTemplateOutlet, FaIconComponent, CourseSidebarComponent, CourseUnenrollmentModalComponent, CourseTitleBarComponent],
    providers: [MetisConversationService],
})
export class CourseOverviewComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private courseExerciseService = inject(CourseExerciseService);
    private teamService = inject(TeamService);
    private websocketService = inject(WebsocketService);
    private serverDateService = inject(ArtemisServerDateService);
    private alertService = inject(AlertService);
    private examParticipationService = inject(ExamParticipationService);
    private sidebarItemService = inject(CourseSidebarItemService);
    private calendarService = inject(CalendarService);
    private courseTitleBarService = inject(CourseTitleBarService);

    // Only shown when a page projects title-bar content (e.g. FAQ); sidebar tabs and plain pages render none.
    protected readonly showCourseTitleBar = computed(() => !!(this.courseTitleBarService.actionsTemplate() || this.courseTitleBarService.titleTemplate()));

    private toggleSidebarEventSubscription?: Subscription;
    private teamAssignmentUpdateListener?: Subscription;
    private quizExercisesChannel?: string;
    private quizExercisesSubscription?: Subscription;
    private examStartedSubscription?: Subscription;

    showUnenrollModal = signal<boolean>(false);
    courseActionItems = signal<CourseActionItem[]>([]);
    canUnenroll = signal<boolean>(false);
    activatedComponentReference = signal<
        CourseExercisesComponent | CourseLecturesComponent | CourseExamsComponent | CourseTutorialGroupsComponent | CourseConversationsComponent | CourseIrisComponent | undefined
    >(undefined);

    // Icons
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faCircleNotch = faCircleNotch;
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;

    override async ngOnInit() {
        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.isSidebarCollapsed.update((value) => {
                const componentRef = this.activatedComponentReference();
                const componentCollapsed = typeof componentRef?.isCollapsed === 'function' ? componentRef.isCollapsed() : componentRef?.isCollapsed;
                return componentCollapsed ?? !value;
            });
        });

        this.subscription = this.route?.params.subscribe((params: Params) => {
            const id = Number(params.courseId);
            const previousCourseId = this.courseId();
            this.courseId.set(id);
            // In-place navigation to a different course (without destroying this container) must reload the course content.
            // Tab access does NOT need re-checking here: Angular's equalParamsAndUrlSegments recurses into the parent chain,
            // so a changed :courseId re-runs the child route's CourseOverviewGuard on its own.
            if (previousCourseId && previousCourseId !== id) {
                // Reset quiz websocket subscription so it points at the new course's topic
                this.quizExercisesSubscription?.unsubscribe();
                this.quizExercisesChannel = '';
                this.subscribeForQuizChanges();
                // loadCourse() unsubscribes any in-flight loadCourseSubscription internally before returning the observable
                this.loadCourseSubscription = this.loadCourse().subscribe({
                    next: () => {
                        this.sidebarItems.set(this.getSidebarItems());
                        this.courseActionItems.set(this.getCourseActionItems());
                    },
                });
            }
        });
        await super.ngOnInit();

        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted: boolean) => {
            this.isExamStarted.set(isStarted);
        });

        this.courseActionItems.set(this.getCourseActionItems());
        const componentRef = this.activatedComponentReference();
        const componentCollapsed = typeof componentRef?.isCollapsed === 'function' ? componentRef.isCollapsed() : componentRef?.isCollapsed;
        this.isSidebarCollapsed.set(componentCollapsed ?? false);
        this.sidebarItems.set(this.getSidebarItems());
        await this.initAfterCourseLoad();
    }

    handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
    }

    async initAfterCourseLoad() {
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    /**
     * Fetches the course from the server including all exercises, lectures, exams and competencies.
     * This is the only place that issues the (expensive) for-dashboard call when navigating into a course;
     * the {@link CourseOverviewGuard} reuses the stored result instead of fetching itself.
     */
    loadCourse(refresh = false): Observable<void> {
        this.refreshingCourse.set(refresh);
        // Loads the course content; the CourseOverviewGuard independently decides tab access via the lightweight access endpoint.
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
            this.loadCourseSubscription = observable.subscribe({
                next: () => this.sidebarItems.set(this.getSidebarItems()),
            });
            this.calendarService.reloadEvents();
        }
        return observable;
    }

    protected getHasSidebar(): boolean {
        return !!this.route.snapshot.firstChild?.data?.hasSidebar;
    }

    protected handleComponentActivation(componentRef: unknown): void {
        if (
            componentRef instanceof CourseExercisesComponent ||
            componentRef instanceof CourseLecturesComponent ||
            componentRef instanceof CourseTutorialGroupsComponent ||
            componentRef instanceof CourseExamsComponent ||
            componentRef instanceof CourseConversationsComponent ||
            componentRef instanceof CourseIrisComponent
        ) {
            this.activatedComponentReference.set(componentRef);
        }

        if (
            componentRef instanceof CourseExercisesComponent ||
            componentRef instanceof CourseLecturesComponent ||
            componentRef instanceof CourseTutorialGroupsComponent ||
            componentRef instanceof CourseExamsComponent ||
            componentRef instanceof CourseConversationsComponent
        ) {
            componentRef.setPageTitle(this.pageTitle());
        }

        const componentCollapsed = readComponentCollapsed(componentRef);
        this.isSidebarCollapsed.set(componentCollapsed ?? false);
    }

    handleToggleSidebar(): void {
        if (!this.activatedComponentReference()) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference();
        childRouteComponent?.toggleSidebar();
        const componentCollapsed = typeof childRouteComponent!.isCollapsed === 'function' ? childRouteComponent!.isCollapsed() : childRouteComponent!.isCollapsed;
        this.isSidebarCollapsed.set(componentCollapsed);
    }

    getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const currentCourse = this.course();

        // Use the service to get sidebar items
        const defaultItems = this.sidebarItemService.getStudentDefaultItems(currentCourse?.trainingEnabled);
        if (currentCourse?.irisEnabledInCourse) {
            defaultItems.unshift(this.sidebarItemService.getIrisItem());
        }
        sidebarItems.push(...defaultItems);

        if (this.lectureEnabled && currentCourse?.lectures) {
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

        if (this.tutorialGroupEnabled && this.hasTutorialGroups()) {
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

        if ((currentCourse?.numberOfAcceptedFaqs ?? 0) > 0) {
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
        this.showUnenrollModal.set(true);
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
        void this.router.navigate(['courses', this.courseId(), 'register']);
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
            this.quizExercisesSubscription = this.websocketService.subscribe<QuizExercise>(this.quizExercisesChannel).subscribe((quizExercise: QuizExercise) => {
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

    override ngOnDestroy() {
        super.ngOnDestroy();
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        this.quizExercisesSubscription?.unsubscribe();
        this.examStartedSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
    }
}
