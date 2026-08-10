import { AfterViewInit, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { Params, RouterOutlet } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { faChartBar, faChevronLeft, faChevronRight, faCircleNotch, faDoorOpen, faEye, faListAlt, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { CourseActionItem, CourseSidebarComponent, SidebarItem } from 'app/course/shared/course-sidebar/course-sidebar.component';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { BaseCourseContainerComponent } from 'app/course/shared/course-base-container/course-base-container.component';
import { CourseSidebarItemService } from 'app/course/shared/services/sidebar-item.service';
import { CourseExercisesComponent } from 'app/course/overview/course-exercises/course-exercises.component';
import { CourseExamsComponent } from 'app/exam/shared/course-exams/course-exams.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures/course-lectures.component';
import { CourseTutorialGroupsComponent } from 'app/tutorialgroup/overview/course-tutorial-groups/course-tutorial-groups.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseUnenrollmentModalComponent } from 'app/course/overview/course-unenrollment-modal/course-unenrollment-modal.component';
import { CourseTitleBarComponent } from 'app/course/shared/course-title-bar/course-title-bar.component';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { CourseIrisComponent } from 'app/iris/overview/course-iris/course-iris.component';
import { CourseOverviewTabDataService } from 'app/course/overview/services/course-overview-tab-data.service';

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
    imports: [CdkScrollable, NgClass, RouterOutlet, NgTemplateOutlet, CourseSidebarComponent, CourseUnenrollmentModalComponent, CourseTitleBarComponent],
    providers: [MetisConversationService],
})
export class CourseOverviewComponent extends BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private alertService = inject(AlertService);
    private examParticipationService = inject(ExamParticipationService);
    private sidebarItemService = inject(CourseSidebarItemService);
    private courseTitleBarService = inject(CourseTitleBarService);
    private courseAvailableTabsService = inject(CourseAvailableTabsService);
    private courseOverviewExercisesService = inject(CourseOverviewExercisesService);
    private courseOverviewTabDataService = inject(CourseOverviewTabDataService);

    /**
     * Every page that does not bring its own sidebar gets the shell title bar, so the student overview matches course
     * management and administration — calendar, statistics, competencies, learning path and quiz training previously
     * had no bar at all. The title comes from the route's `pageTitle`.
     *
     * The sidebar pages (exercises, lectures, exams, communication, tutorial groups) are excluded because their
     * sidebar header already carries the page identity and the collapse control; a second bar above it would only
     * repeat it. Those pages can still project content, which is what keeps the communication search bar working.
     */
    protected readonly showCourseTitleBar = computed(() => !this.hasSidebar() || !!(this.courseTitleBarService.actionsTemplate() || this.courseTitleBarService.titleTemplate()));

    private toggleSidebarEventSubscription?: Subscription;
    private examStartedSubscription?: Subscription;
    private availableTabsSubscription?: Subscription;

    /**
     * Which course tabs are available to the user. Server-computed and shared with the {@link CourseOverviewGuard}
     * via {@link CourseAvailableTabsService}, so the sidebar and the guard can never disagree.
     */
    availableTabs = signal<CourseAvailableTabs | undefined>(undefined);
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
            // In-place navigation to a different course (without destroying this container) must reload the course
            // content and the available tabs. loadCourse() does both; the tabs are keyed by course id, so the new
            // course's tabs are always fetched rather than the previous course's being reused.
            if (previousCourseId && previousCourseId !== id) {
                // The exercise data of the previous course must not be reused for the new one
                this.courseOverviewExercisesService.clear();
                this.courseOverviewTabDataService.clear();
                // loadCourse() unsubscribes any in-flight loadCourseSubscription internally before returning the observable
                this.loadCourseSubscription = this.loadCourse().subscribe({
                    next: () => {
                        this.sidebarItems.set(this.getSidebarItems());
                        this.courseActionItems.set(this.getCourseActionItems());
                    },
                    error: () => this.handleLoadCourseError(),
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
    }

    handleCourseIdChange(courseId: number): void {
        this.courseId.set(courseId);
    }

    /**
     * Fetches the course itself and, in parallel, which tabs are available for it.
     *
     * Neither request carries course content: exercises, participations and scores are loaded by the exercises and
     * statistics tabs (via {@link CourseOverviewExercisesService}) and lectures by the lectures tab, so entering a
     * course on any tab only pays for what that tab shows. The two requests are issued together, keeping entering a
     * course to a single round trip of latency.
     */
    loadCourse(): Observable<void> {
        this.loadAvailableTabs();
        const observable = this.courseManagementService.findCourseForOverview(this.courseId()).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course.set(res.body);
                }
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
        this.loadCourseSubscription?.unsubscribe();
        return observable;
    }

    /**
     * Loads which tabs are available for the current course and updates the sidebar from the result.
     *
     * The value the guard already fetched for this navigation is reused, so entering a guarded tab costs one
     * `available-tabs` request rather than two.
     *
     * A failure here is deliberately not alerted: the parallel course load reports the same network problem. The old
     * course's flags are cleared before starting so an in-place course switch can never retain links that are not
     * available in the new course; on failure the sidebar therefore stays at its safe, always-available baseline.
     */
    /**
     * Keeps the sidebar's tab list in step with the guard on every navigation inside the course.
     *
     * The container outlives child-tab navigations, so its flags would otherwise be whatever the course load found on
     * entry, while the guard decides each guarded navigation from a freshly fetched answer. The two could then
     * disagree: a tab removed since entry would still be offered, and one that became available would stay hidden.
     *
     * Costs nothing on a guarded navigation — the tab service is scoped to the navigation, so the guard's response is
     * still the one being shared. An unguarded tab pays a single lightweight request.
     */
    protected override handleNavigationEndActions(): void {
        const courseId = this.courseId();
        if (!courseId) {
            return;
        }
        this.availableTabsSubscription?.unsubscribe();
        this.availableTabsSubscription = this.courseAvailableTabsService.loadIfNeeded(courseId).subscribe({
            next: (tabs) => {
                this.availableTabs.set(tabs);
                this.sidebarItems.set(this.getSidebarItems());
            },
            // Leave the current flags in place: a failed refresh must not empty the sidebar the user is looking at
            error: () => {},
        });
    }

    private loadAvailableTabs(): void {
        const courseId = this.courseId();
        this.availableTabs.set(undefined);
        this.sidebarItems.set(this.getSidebarItems());
        const tabs$ = this.courseAvailableTabsService.loadIfNeeded(courseId);
        this.availableTabsSubscription?.unsubscribe();
        this.availableTabsSubscription = tabs$.subscribe({
            next: (tabs) => {
                this.availableTabs.set(tabs);
                this.sidebarItems.set(this.getSidebarItems());
            },
            error: () => {},
        });
    }

    /**
     * Terminal error observer for `loadCourse()`.
     * <p>
     * `loadCourse()` deliberately rethrows every non-403 error after alerting the user, so each subscriber has to
     * provide an error observer — otherwise RxJS reports it as an unhandled error and it surfaces through the global
     * error handler. The user-facing alert already happens inside the pipe, so there is nothing left to do here beyond
     * terminating the stream cleanly.
     */
    private handleLoadCourseError(): void {
        // intentionally empty: the user-facing alert is already raised in loadCourse()'s catchError
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

    /**
     * Builds the sidebar from the server-computed available tabs. The same value backs the {@link CourseOverviewGuard},
     * so a tab is offered here exactly when opening it is allowed — the two cannot drift apart.
     *
     * Before the tabs have arrived only the always-available entries are rendered; `loadAvailableTabs` rebuilds the
     * sidebar once they do.
     */
    getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const tabs = this.availableTabs();

        // Use the service to get sidebar items
        const defaultItems = this.sidebarItemService.getStudentDefaultItems(tabs?.training);
        if (tabs?.iris) {
            defaultItems.unshift(this.sidebarItemService.getIrisItem());
        }
        sidebarItems.push(...defaultItems);

        if (this.lectureEnabled && tabs?.lectures) {
            const lecturesItem = this.sidebarItemService.getLecturesItem();
            sidebarItems.splice(-2, 0, lecturesItem);
        }

        if (tabs?.exams) {
            const examsItem = this.sidebarItemService.getExamsItem();
            sidebarItems.unshift(examsItem);
        }

        if (tabs?.communication) {
            const communicationsItem = this.sidebarItemService.getCommunicationsItem();
            sidebarItems.push(communicationsItem);
        }

        if (this.tutorialGroupEnabled && tabs?.tutorialGroups) {
            const tutorialGroupsItem = this.sidebarItemService.getTutorialGroupsItem();
            sidebarItems.push(tutorialGroupsItem);
        }

        if (this.atlasEnabled && tabs?.competencies) {
            const competenciesItem = this.sidebarItemService.getCompetenciesItem();
            sidebarItems.push(competenciesItem);

            if (tabs.learningPaths) {
                const learningPathItem = this.sidebarItemService.getLearningPathItem();
                sidebarItems.push(learningPathItem);
            }
        }

        if (tabs?.faq) {
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

    override ngOnDestroy() {
        super.ngOnDestroy();
        this.availableTabsSubscription?.unsubscribe();
        // Drop what is held for the current navigation so nothing outlives the course being left
        this.courseAvailableTabsService.clear();
        this.courseOverviewExercisesService.clear();
        this.courseOverviewTabDataService.clear();
        this.examStartedSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
    }
}
