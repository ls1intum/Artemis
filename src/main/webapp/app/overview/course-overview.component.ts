import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import {
    AfterViewInit,
    ChangeDetectorRef,
    Component,
    EmbeddedViewRef,
    HostListener,
    OnDestroy,
    OnInit,
    QueryList,
    TemplateRef,
    ViewChild,
    ViewChildren,
    ViewContainerRef,
    inject,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
    IconDefinition,
    faChalkboardUser,
    faChartBar,
    faChevronLeft,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faComments,
    faDoorOpen,
    faEllipsis,
    faEye,
    faFlag,
    faGraduationCap,
    faListAlt,
    faListCheck,
    faNetworkWired,
    faPersonChalkboard,
    faQuestion,
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import dayjs from 'dayjs/esm';
import { Observable, Subject, Subscription, catchError, firstValueFrom, map, of, takeUntil, throwError } from 'rxjs';
import { facSidebar } from '../../content/icons/icons';
import { CourseManagementService } from '../course/manage/course-management.service';
import { CourseExercisesComponent } from './course-exercises/course-exercises.component';
import { CourseLecturesComponent } from './course-lectures/course-lectures.component';
import { CourseExamsComponent } from './course-exams/course-exams.component';
import { CourseTutorialGroupsComponent } from './course-tutorial-groups/course-tutorial-groups.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { sortCourses } from 'app/shared/util/course.util';
import { CourseUnenrollmentModalComponent } from './course-unenrollment-modal.component';
import { LtiService } from 'app/shared/service/lti.service';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';

interface CourseActionItem {
    title: string;
    icon?: IconDefinition;
    translation: string;
    action?: (item?: CourseActionItem) => void;
}

interface SidebarItem {
    routerLink: string;
    icon?: IconDefinition;
    title: string;
    testId?: string;
    translation: string;
    hasInOrionProperty?: boolean;
    showInOrionWindow?: boolean;
    guidedTour?: boolean;
    featureToggle?: FeatureToggle;
    hidden: boolean;
}

@Component({
    selector: 'jhi-course-overview',
    templateUrl: './course-overview.component.html',
    styleUrls: ['course-overview.scss', 'course-overview.component.scss'],
    providers: [MetisConversationService],
})
export class CourseOverviewComponent implements OnInit, OnDestroy, AfterViewInit {
    private ngUnsubscribe = new Subject<void>();
    private closeSidebarEventSubscription: Subscription;
    private openSidebarEventSubscription: Subscription;
    private toggleSidebarEventSubscription: Subscription;

    // course id of the course that is currently displayed
    private courseId: number;
    private subscription: Subscription;
    dashboardSubscription: Subscription;
    // currently displayed course
    course?: Course;
    // all courses of the current user, used for the dropdown menu
    courses?: Course[];
    refreshingCourse = false;
    private teamAssignmentUpdateListener: Subscription;
    private quizExercisesChannel: string;
    hasUnreadMessages: boolean;
    communicationRouteLoaded: boolean;
    isProduction = true;
    isTestServer = false;
    pageTitle: string;
    hasSidebar: boolean = false;
    sidebarItems: SidebarItem[];
    courseActionItems: CourseActionItem[];
    isNotManagementView: boolean;
    canUnenroll: boolean;
    isNavbarCollapsed = false;
    isSidebarCollapsed = false;
    profileSubscription?: Subscription;
    showRefreshButton: boolean = false;
    isExamStarted = false;
    private examStartedSubscription: Subscription;
    readonly MIN_DISPLAYED_COURSES: number = 6;
    isLti: boolean = false;
    private ltiSubscription: Subscription;

    // Properties to track hidden items for dropdown menu
    dropdownOpen: boolean = false;
    anyItemHidden: boolean = false;
    hiddenItems: SidebarItem[] = [];
    thresholdsForEachSidebarItem: number[] = [];
    dropdownOffset: number;
    dropdownClickNumber: number = 0;
    readonly WINDOW_OFFSET: number = 300;
    readonly ITEM_HEIGHT: number = 38;
    readonly BREADCRUMB_AND_NAVBAR_HEIGHT: number = 88;

    private conversationServiceInstantiated = false;
    private checkedForUnreadMessages = false;
    activatedComponentReference: CourseExercisesComponent | CourseLecturesComponent | CourseExamsComponent | CourseTutorialGroupsComponent | CourseConversationsComponent;

    // Rendered embedded view for controls in the bar so we can destroy it if needed
    private controlsEmbeddedView?: EmbeddedViewRef<any>;
    // Subscription for the course fetching
    private loadCourseSubscription?: Subscription;
    // Subscription to listen to changes on the control configuration
    private controlsSubscription?: Subscription;
    // Subscription to listen for the ng-container for controls to be mounted
    private vcSubscription?: Subscription;
    // The current controls template from the sub-route component to render
    private controls?: TemplateRef<any>;
    // The current controls configuration from the sub-route component
    public controlConfiguration?: BarControlConfiguration;

    // ng-container mount point extracted from our own template so we can render sth in it
    @ViewChild('controlsViewContainer', { read: ViewContainerRef }) controlsViewContainer: ViewContainerRef;
    // Using a list query to be able to listen for changes (late mount); need both as this only returns native nodes
    @ViewChildren('controlsViewContainer') controlsViewContainerAsList: QueryList<ViewContainerRef>;

    @ViewChild('itemsDrop', { static: true }) itemsDrop: NgbDropdown;

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
    faEllipsis = faEllipsis;
    faQuestion = faQuestion;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;

    readonly isMessagingEnabled = isMessagingEnabled;
    readonly isCommunicationEnabled = isCommunicationEnabled;

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);

    constructor(
        private courseService: CourseManagementService,
        private courseExerciseService: CourseExerciseService,
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private teamService: TeamService,
        private jhiWebsocketService: JhiWebsocketService,
        private serverDateService: ArtemisServerDateService,
        private alertService: AlertService,
        private changeDetectorRef: ChangeDetectorRef,
        private metisConversationService: MetisConversationService,
        private router: Router,
        private courseAccessStorageService: CourseAccessStorageService,
        private profileService: ProfileService,
        private modalService: NgbModal,
        private examParticipationService: ExamParticipationService,
        private ltiService: LtiService,
    ) {}

    async ngOnInit() {
        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.isSidebarCollapsed = true;
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.isSidebarCollapsed = false;
        });

        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.isSidebarCollapsed = this.activatedComponentReference?.isCollapsed ?? !this.isSidebarCollapsed;
        });
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
        });
        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted) => {
            this.isExamStarted = isStarted;
        });
        this.getCollapseStateFromStorage();
        this.course = this.courseStorageService.getCourse(this.courseId);
        this.isNotManagementView = !this.router.url.startsWith('/course-management');
        // Notify the course access storage service that the course has been accessed
        // If course is not active, it means that it is accessed from course archive, which should not
        // be stored in local storage and therefore displayed in recently accessed
        if (this.course && this.isCourseActive(this.course)) {
            this.courseAccessStorageService.onCourseAccessed(
                this.courseId,
                CourseAccessStorageService.STORAGE_KEY,
                CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
            );
            this.courseAccessStorageService.onCourseAccessed(
                this.courseId,
                CourseAccessStorageService.STORAGE_KEY_DROPDOWN,
                CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_DROPDOWN,
            );
        }

        await firstValueFrom(this.loadCourse());
        await this.initAfterCourseLoad();
        this.sidebarItems = this.getSidebarItems();
        this.courseActionItems = this.getCourseActionItems();
        this.updateVisibleNavbarItems(window.innerHeight);
        await this.updateRecentlyAccessedCourses();
        this.isSidebarCollapsed = this.activatedComponentReference?.isCollapsed ?? false;
        this.ltiSubscription = this.ltiService.isLti$.subscribe((isLti) => {
            this.isLti = isLti;
        });
    }

    /** Listen window resize event by height */
    @HostListener('window: resize', ['$event'])
    onResize() {
        if (this.itemsDrop) {
            this.updateVisibleNavbarItems(window.innerHeight);
            if (!this.anyItemHidden) this.itemsDrop.close();
        }
    }

    /** Update sidebar item's hidden property based on the window height to display three-dots */
    updateVisibleNavbarItems(height: number) {
        const threshold = this.calculateThreshold();
        this.applyThreshold(threshold, height);
    }

    /**  Applies the visibility threshold to sidebar items, determining which items should be hidden.*/
    private applyThreshold(threshold: number, height: number) {
        this.anyItemHidden = false;
        this.hiddenItems = [];
        // Reverse the sidebar items to remove items starting from the bottom
        const reversedSidebarItems = [...this.sidebarItems].reverse();
        reversedSidebarItems.forEach((item, index) => {
            const currentThreshold = threshold - index * this.ITEM_HEIGHT;
            item.hidden = height <= currentThreshold;
            if (item.hidden) {
                this.anyItemHidden = true;
                this.hiddenItems.unshift(item);
            }
        });
    }

    /** Calculate threshold levels based on the number of entries in the sidebar */
    calculateThreshold(): number {
        return this.sidebarItems.length * this.ITEM_HEIGHT + this.WINDOW_OFFSET;
    }

    /** initialize courses attribute by retrieving all courses from the server */
    async updateRecentlyAccessedCourses() {
        this.dashboardSubscription = this.courseService.findAllForDropdown().subscribe({
            next: (res: HttpResponse<Course[]>) => {
                if (res.body) {
                    const courses: Course[] = [];
                    res.body?.forEach((course) => {
                        courses.push(course);
                    });
                    this.courses = sortCourses(courses);
                    if (this.courses.length > this.MIN_DISPLAYED_COURSES) {
                        const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY_DROPDOWN);
                        this.courses = this.courses.filter((course) => lastAccessedCourseIds.includes(course.id!));
                    }
                    this.courses = this.courses.filter((course) => course.id !== this.courseId);
                }
            },
        });
    }

    /** Navigate to a new Course */
    switchCourse(course: Course) {
        this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
            this.router.navigate(['courses', course.id, 'exercises']);
        });
    }

    getCourseActionItems(): CourseActionItem[] {
        const courseActionItems = [];
        this.canUnenroll = this.canStudentUnenroll() && !this.course?.isAtLeastTutor;
        if (this.canUnenroll) {
            const unenrollItem: CourseActionItem = this.getUnenrollItem();
            courseActionItems.push(unenrollItem);
        }
        return courseActionItems;
    }

    getSidebarItems(): SidebarItem[] {
        const sidebarItems = this.getDefaultItems();
        if (this.course?.lectures) {
            const lecturesItem: SidebarItem = this.getLecturesItems();
            sidebarItems.splice(-1, 0, lecturesItem);
        }
        if (this.course?.exams && this.hasVisibleExams()) {
            const examsItem: SidebarItem = this.getExamsItems();
            sidebarItems.unshift(examsItem);
        }
        if (isCommunicationEnabled(this.course)) {
            const communicationsItem: SidebarItem = this.getCommunicationsItems();
            sidebarItems.push(communicationsItem);
        }

        if (this.hasTutorialGroups()) {
            const tutorialGroupsItem: SidebarItem = this.getTutorialGroupsItems();
            sidebarItems.push(tutorialGroupsItem);
        }

        if (this.hasCompetencies()) {
            const competenciesItem: SidebarItem = this.getCompetenciesItems();
            sidebarItems.push(competenciesItem);
            if (this.course?.learningPathsEnabled) {
                const learningPathItem: SidebarItem = this.getLearningPathItems();
                sidebarItems.push(learningPathItem);
            }
        }

        if (this.course?.faqEnabled) {
            const faqItem: SidebarItem = this.getFaqItem();
            sidebarItems.push(faqItem);
        }

        return sidebarItems;
    }

    getUnenrollItem() {
        const unenrollItem: CourseActionItem = {
            title: 'Unenroll',
            icon: faDoorOpen,
            translation: 'artemisApp.courseOverview.exerciseList.details.unenrollmentButton',
            action: () => this.openUnenrollStudentModal(),
        };
        return unenrollItem;
    }

    getLecturesItems() {
        const lecturesItem: SidebarItem = {
            routerLink: 'lectures',
            icon: faChalkboardUser,
            title: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return lecturesItem;
    }

    getExamsItems() {
        const examsItem: SidebarItem = {
            routerLink: 'exams',
            icon: faGraduationCap,
            title: 'Exams',
            testId: 'exam-tab',
            translation: 'artemisApp.courseOverview.menu.exams',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return examsItem;
    }

    getCommunicationsItems() {
        const communicationsItem: SidebarItem = {
            routerLink: 'communication',
            icon: faComments,
            title: 'Communication',
            translation: 'artemisApp.courseOverview.menu.communication',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return communicationsItem;
    }

    getTutorialGroupsItems() {
        const tutorialGroupsItem: SidebarItem = {
            routerLink: 'tutorial-groups',
            icon: faPersonChalkboard,
            title: 'Tutorials',
            translation: 'artemisApp.courseOverview.menu.tutorialGroups',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.TutorialGroups,
            hidden: false,
        };
        return tutorialGroupsItem;
    }

    getCompetenciesItems() {
        const competenciesItem: SidebarItem = {
            routerLink: 'competencies',
            icon: faFlag,
            title: 'Competencies',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return competenciesItem;
    }

    getLearningPathItems() {
        const learningPathItem: SidebarItem = {
            routerLink: 'learning-path',
            icon: faNetworkWired,
            title: 'Learning Path',
            translation: 'artemisApp.courseOverview.menu.learningPath',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.LearningPaths,
            hidden: false,
        };
        return learningPathItem;
    }

    getDashboardItems() {
        const dashboardItem: SidebarItem = {
            routerLink: 'dashboard',
            icon: faChartBar,
            title: 'Dashboard',
            translation: 'artemisApp.courseOverview.menu.dashboard',
            hasInOrionProperty: false,
            showInOrionWindow: false,
            featureToggle: FeatureToggle.StudentCourseAnalyticsDashboard,
            hidden: false,
        };
        return dashboardItem;
    }

    getFaqItem() {
        const faqItem: SidebarItem = {
            routerLink: 'faq',
            icon: faQuestion,
            title: 'FAQs',
            translation: 'artemisApp.courseOverview.menu.faq',
            hasInOrionProperty: false,
            showInOrionWindow: false,
            hidden: false,
        };
        return faqItem;
    }

    getDefaultItems() {
        const items = [];
        if (this.course?.studentCourseAnalyticsDashboardEnabled) {
            const dashboardItem: SidebarItem = this.getDashboardItems();
            items.push(dashboardItem);
        }
        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faListCheck,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'statistics',
            icon: faListAlt,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            guidedTour: true,
            hidden: false,
        };

        return items.concat([exercisesItem, statisticsItem]);
    }

    async initAfterCourseLoad() {
        await this.subscribeToTeamAssignmentUpdates();
        this.subscribeForQuizChanges();
    }

    private setUpConversationService() {
        if (!isMessagingEnabled(this.course) && !isCommunicationEnabled(this.course)) {
            return;
        }

        if (!this.conversationServiceInstantiated && this.communicationRouteLoaded) {
            this.metisConversationService
                .setUpConversationService(this.course!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    complete: () => {
                        this.conversationServiceInstantiated = true;
                        // service is fully set up, now we can subscribe to the respective observables
                        this.subscribeToHasUnreadMessages();
                    },
                });
        } else if (!this.checkedForUnreadMessages && isMessagingEnabled(this.course)) {
            this.metisConversationService.checkForUnreadMessages(this.course!);
            this.subscribeToHasUnreadMessages();
            this.checkedForUnreadMessages = true;
        }
    }

    canStudentUnenroll(): boolean {
        return !!this.course?.unenrollmentEnabled && dayjs().isBefore(this.course?.unenrollmentEndDate);
    }

    courseActionItemClick(item?: CourseActionItem) {
        if (item?.action) {
            item.action(item);
        }
    }

    openUnenrollStudentModal() {
        const modalRef = this.modalService.open(CourseUnenrollmentModalComponent, { size: 'xl' });
        modalRef.componentInstance.course = this.course;
    }

    ngAfterViewInit() {
        // Check if controls mount point is available, if not, wait for it
        if (this.controlsViewContainer) {
            this.tryRenderControls();
        } else {
            this.vcSubscription = this.controlsViewContainerAsList.changes.subscribe(() => this.tryRenderControls());
        }
    }

    private subscribeToHasUnreadMessages() {
        this.metisConversationService.hasUnreadMessages$.pipe().subscribe((hasUnreadMessages: boolean) => {
            this.hasUnreadMessages = hasUnreadMessages ?? false;
        });
    }

    /**
     * Accepts a component reference of the subcomponent rendered based on the current route.
     * If it provides a controlsConfiguration, we try to render the controls component
     * @param componentRef the sub route component that has been mounted into the router outlet
     */
    onSubRouteActivate(componentRef: any) {
        this.getPageTitle();
        this.getShowRefreshButton();
        this.communicationRouteLoaded = this.route.snapshot.firstChild?.routeConfig?.path === 'communication';

        this.setUpConversationService();

        this.hasSidebar = this.getHasSidebar();

        if (componentRef.controlConfiguration) {
            const provider = componentRef as BarControlConfigurationProvider;
            this.controlConfiguration = provider.controlConfiguration as BarControlConfiguration;

            // Listen for changes to the control configuration; works for initial config as well
            this.controlsSubscription =
                this.controlConfiguration.subject?.subscribe((controls: TemplateRef<any>) => {
                    this.controls = controls;
                    this.tryRenderControls();
                }) || undefined;
        }
        if (
            componentRef instanceof CourseExercisesComponent ||
            componentRef instanceof CourseLecturesComponent ||
            componentRef instanceof CourseTutorialGroupsComponent ||
            componentRef instanceof CourseExamsComponent ||
            componentRef instanceof CourseConversationsComponent
        ) {
            this.activatedComponentReference = componentRef;
        }

        // Since we change the pageTitle + might be pulling data upwards during a render cycle, we need to re-run change detection
        this.changeDetectorRef.detectChanges();

        this.isSidebarCollapsed = this.activatedComponentReference?.isCollapsed ?? false;
    }

    toggleSidebar() {
        if (!this.activatedComponentReference) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference;
        childRouteComponent.toggleSidebar();
        this.isSidebarCollapsed = childRouteComponent.isCollapsed;
    }

    @HostListener('window:keydown.Control.Shift.b', ['$event'])
    onKeyDownControlShiftB(event: KeyboardEvent) {
        event.preventDefault();
        this.toggleSidebar();
    }

    getPageTitle(): void {
        const routePageTitle: string = this.route.snapshot.firstChild?.data?.pageTitle;
        this.pageTitle = routePageTitle?.substring(routePageTitle.indexOf('.') + 1);
    }

    getShowRefreshButton(): void {
        this.showRefreshButton = this.route.snapshot.firstChild?.data?.showRefreshButton ?? false;
    }

    getHasSidebar(): boolean {
        return this.route.snapshot.firstChild?.data?.hasSidebar;
    }

    /**
     * Removes the controls component from the DOM and cancels the listener for controls changes.
     * Called by the router outlet as soon as the currently mounted component is removed
     */
    onSubRouteDeactivate() {
        this.removeCurrentControlsView();
        this.controls = undefined;
        this.controlConfiguration = undefined;
        this.controlsSubscription?.unsubscribe();
        this.changeDetectorRef.detectChanges();
    }

    private removeCurrentControlsView() {
        this.controlsEmbeddedView?.detach();
        this.controlsEmbeddedView?.destroy();
    }

    /**
     * Mounts the controls as specified by the currently mounted sub-route component to the ng-container in the top bar
     * if all required data is available.
     */
    tryRenderControls() {
        if (this.controlConfiguration && this.controls && this.controlsViewContainer) {
            this.removeCurrentControlsView();
            this.controlsEmbeddedView = this.controlsViewContainer.createEmbeddedView(this.controls);
            this.controlsEmbeddedView.detectChanges();
        }
    }

    /**
     * Determines whether the user can register for the course by trying to fetch the for-registration version
     */
    canRegisterForCourse(): Observable<boolean> {
        return this.courseService.findOneForRegistration(this.courseId).pipe(
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
        this.router.navigate(['courses', this.courseId, 'register']);
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
     * Fetch the course from the server including all exercises, lectures, exams and competencies
     * @param refresh Whether this is a force refresh (displays loader animation)
     */
    loadCourse(refresh = false): Observable<void> {
        this.refreshingCourse = refresh;
        const observable = this.courseService.findOneForDashboard(this.courseId).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course = res.body;
                }

                if (refresh) {
                    this.setUpConversationService();
                }

                setTimeout(() => (this.refreshingCourse = false), 500); // ensure min animation duration
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
        }
        return observable;
    }

    ngOnDestroy() {
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.quizExercisesChannel) {
            this.jhiWebsocketService.unsubscribe(this.quizExercisesChannel);
        }
        this.loadCourseSubscription?.unsubscribe();
        this.controlsSubscription?.unsubscribe();
        this.vcSubscription?.unsubscribe();
        this.subscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.examStartedSubscription?.unsubscribe();
        this.dashboardSubscription?.unsubscribe();
        this.closeSidebarEventSubscription?.unsubscribe();
        this.openSidebarEventSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.ltiSubscription?.unsubscribe();
    }

    subscribeForQuizChanges() {
        // subscribe to quizzes which get visible
        if (!this.quizExercisesChannel) {
            this.quizExercisesChannel = '/topic/courses/' + this.courseId + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExercisesChannel);
            this.jhiWebsocketService.receive(this.quizExercisesChannel).subscribe((quizExercise: QuizExercise) => {
                quizExercise = this.courseExerciseService.convertExerciseDatesFromServer(quizExercise);
                // the quiz was set to visible or started, we should add it to the exercise list and display it at the top
                if (this.course && this.course.exercises) {
                    this.course.exercises = this.course.exercises.filter((exercise) => exercise.id !== quizExercise.id);
                    this.course.exercises.push(quizExercise);
                }
            });
        }
    }

    /**
     * check if there is at least one exam which should be shown
     */
    hasVisibleExams(): boolean {
        if (this.course?.exams) {
            for (const exam of this.course.exams) {
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
        return !!(this.course?.numberOfCompetencies || this.course?.numberOfPrerequisites);
    }

    /**
     * Check if the course has a tutorial groups
     */
    hasTutorialGroups(): boolean {
        return !!this.course?.numberOfTutorialGroups;
    }

    /**
     * Receives team assignment changes and updates related attributes of the affected exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        const teamAssignmentUpdates = await this.teamService.teamAssignmentUpdates;
        this.teamAssignmentUpdateListener = teamAssignmentUpdates.subscribe((teamAssignment: TeamAssignmentPayload) => {
            const exercise = this.course?.exercises?.find((courseExercise) => courseExercise.id === teamAssignment.exerciseId);
            if (exercise) {
                exercise.studentAssignedTeamId = teamAssignment.teamId;
                exercise.studentParticipations = teamAssignment.studentParticipations;
            }
        });
    }

    @HostListener('window:keydown.Control.m', ['$event'])
    onKeyDownControlM(event: KeyboardEvent) {
        event.preventDefault();
        this.toggleCollapseState();
    }

    getCollapseStateFromStorage() {
        const storedCollapseState: string | null = localStorage.getItem('navbar.collapseState');
        if (storedCollapseState) this.isNavbarCollapsed = JSON.parse(storedCollapseState);
    }

    toggleCollapseState() {
        this.isNavbarCollapsed = !this.isNavbarCollapsed;
        localStorage.setItem('navbar.collapseState', JSON.stringify(this.isNavbarCollapsed));
    }

    /**
     * A course is active if the end date is after the current date or
     * end date is not set at all
     *
     * @param course The given course to be checked if it is active
     * @returns true if the course is active, otherwise false
     */
    isCourseActive(course: Course): boolean {
        return course.endDate ? dayjs(course.endDate).isAfter(dayjs()) : true;
    }
}
