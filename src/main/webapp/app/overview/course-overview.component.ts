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
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
    IconDefinition,
    faChalkboardUser,
    faChartBar,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faComment,
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
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
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
import { CourseTutorialGroupsComponent } from './course-tutorial-groups/course-tutorial-groups.component';
import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { sortCourses } from 'app/shared/util/course.util';
import { CourseUnenrollmentModalComponent } from './course-unenrollment-modal.component';

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
    messagesRouteLoaded: boolean;
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
    profileSubscription?: Subscription;
    showRefreshButton: boolean = false;
    readonly MIN_DISPLAYED_COURSES: number = 6;

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
    activatedComponentReference: CourseExercisesComponent | CourseLecturesComponent | CourseTutorialGroupsComponent | CourseConversationsComponent;

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
    facSidebar = facSidebar;
    faEllipsis = faEllipsis;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;

    readonly isMessagingEnabled = isMessagingEnabled;
    readonly isCommunicationEnabled = isCommunicationEnabled;

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
    ) {}

    async ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
        });
        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
        this.getCollapseStateFromStorage();
        this.course = this.courseStorageService.getCourse(this.courseId);
        this.isNotManagementView = !this.router.url.startsWith('/course-management');
        // Notify the course access storage service that the course has been accessed
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

        await firstValueFrom(this.loadCourse());
        await this.initAfterCourseLoad();
        this.sidebarItems = this.getSidebarItems();
        this.courseActionItems = this.getCourseActionItems();
        this.updateVisibility(window.innerHeight);
        this.updateMenuPosition();
        await this.updateRecentlyAccessedCourses();
    }

    /** Listen window resize event by height */
    @HostListener('window: resize', ['$event'])
    onResize() {
        this.dropdownOpen = false;
        this.dropdownClickNumber = 0;
        this.updateVisibility(window.innerHeight);
        this.updateMenuPosition();
    }

    /** Listen click event whether on outside of the menu or one of the items in the menu to close the dropdown menu */
    @HostListener('document: click', ['$event'])
    onClickCloseDropdownMenu() {
        if (this.dropdownOpen) {
            this.dropdownClickNumber += 1;
            if (this.dropdownClickNumber === 2) {
                this.dropdownOpen = false;
                this.dropdownClickNumber = 0;
            }
        }
    }

    /** Update sidebar item's hidden property based on the window height to display three-dots */
    updateVisibility(height: number) {
        let thresholdLevelForCurrentSidebar = this.calculateThreshold();
        this.anyItemHidden = false;
        this.hiddenItems = [];

        for (let i = 0; i < this.sidebarItems.length - 1; i++) {
            this.thresholdsForEachSidebarItem.unshift(thresholdLevelForCurrentSidebar);
            thresholdLevelForCurrentSidebar -= this.ITEM_HEIGHT;
        }
        this.thresholdsForEachSidebarItem.unshift(0);

        this.sidebarItems.forEach((item, index) => {
            item.hidden = height <= this.thresholdsForEachSidebarItem[index];
            if (item.hidden) {
                this.anyItemHidden = true;
                this.hiddenItems.push(item);
            }
        });
    }

    /** Calculate dropdown-menu position based on the number of entries in the sidebar */
    updateMenuPosition() {
        const leftSidebarItems: number = this.sidebarItems.length - this.hiddenItems.length;
        this.dropdownOffset = leftSidebarItems * this.ITEM_HEIGHT + this.BREADCRUMB_AND_NAVBAR_HEIGHT;
    }

    /** Calculate threshold levels based on the number of entries in the sidebar */
    calculateThreshold(): number {
        const numberOfSidebarItems: number = this.sidebarItems.length;
        return numberOfSidebarItems * this.ITEM_HEIGHT + this.WINDOW_OFFSET;
    }

    toggleDropdown() {
        this.dropdownOpen = !this.dropdownOpen;
        // Refresh click numbers after toggle
        if (!this.dropdownOpen) {
            this.dropdownClickNumber = 0;
        }
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
            this.router.navigate(['courses', course.id]);
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
            const communicationItem: SidebarItem = this.getCommunicationItems();
            sidebarItems.push(communicationItem);
        }

        if (isMessagingEnabled(this.course) || isCommunicationEnabled(this.course)) {
            const messagesItem: SidebarItem = this.getMessagesItems();
            sidebarItems.push(messagesItem);
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

    getCommunicationItems() {
        const communicationItem: SidebarItem = {
            routerLink: 'discussion',
            icon: faComment,
            title: 'Communication',
            translation: 'artemisApp.courseOverview.menu.communication',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return communicationItem;
    }

    getMessagesItems() {
        const messagesItem: SidebarItem = {
            routerLink: 'messages',
            icon: faComments,
            title: 'Messages',
            translation: 'artemisApp.courseOverview.menu.messages',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            hidden: false,
        };
        return messagesItem;
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

        if (!this.conversationServiceInstantiated && (this.messagesRouteLoaded || this.communicationRouteLoaded)) {
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
        this.messagesRouteLoaded = this.route.snapshot.firstChild?.routeConfig?.path === 'messages';
        this.communicationRouteLoaded = this.route.snapshot.firstChild?.routeConfig?.path === 'discussion';

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
            componentRef instanceof CourseConversationsComponent
        ) {
            this.activatedComponentReference = componentRef;
        }

        // Since we change the pageTitle + might be pulling data upwards during a render cycle, we need to re-run change detection
        this.changeDetectorRef.detectChanges();
    }

    toggleSidebar() {
        if (!this.activatedComponentReference) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference;
        childRouteComponent.toggleSidebar();
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
        this.dashboardSubscription?.unsubscribe();
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
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
}
