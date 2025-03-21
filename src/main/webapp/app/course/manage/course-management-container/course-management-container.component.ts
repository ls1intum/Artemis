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
    computed,
    effect,
    inject,
    signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, firstValueFrom } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import {
    faChalkboardUser,
    faChartBar,
    faChartColumn,
    faChevronLeft,
    faChevronRight,
    faCircleNotch,
    faClipboard,
    faComments,
    faEye,
    faFlag,
    faGraduationCap,
    faList,
    faListAlt,
    faNetworkWired,
    faPersonChalkboard,
    faPuzzlePiece,
    faQuestion,
    faRobot,
    faSync,
    faTable,
    faTableCells,
    faTimes,
    faUserCheck,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { facSidebar } from 'app/icons/icons';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { LtiService } from 'app/shared/service/lti.service';
import { PROFILE_ATLAS, PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { sortCourses } from 'app/shared/util/course.util';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { ButtonSize } from 'app/shared/components/button.component';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { CourseAccessStorageService } from 'app/course/shared/course-access-storage.service';
import { CourseSidebarComponent, SidebarItem } from 'app/course/shared/course-sidebar/course-sidebar.component';
import { LectureComponent } from 'app/lecture/manage/lecture.component';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations.component';
import { TutorialGroupsChecklistComponent } from 'app/tutorialgroup/manage/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CourseSidebarService } from 'app/course/overview/course-sidebar.service';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/manage/settings/iris-course-settings-update/iris-course-settings-update.component';
import { CompetencyManagementComponent } from 'app/atlas/manage/competency-management/competency-management.component';
import { LearningPathInstructorPageComponent } from 'app/atlas/manage/learning-path-instructor-page/learning-path-instructor-page.component';
import { AssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/assessment-dashboard.component';
import { CourseScoresComponent } from 'app/course/manage/course-scores/course-scores.component';
import { BuildQueueComponent } from 'app/buildagent/build-queue/build-queue.component';
import { FaqComponent } from 'app/communication/faq/faq.component';
import { EventManager } from 'app/shared/service/event-manager.service';

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
    ],
})
export class CourseManagementContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private eventManager = inject(EventManager);
    private courseManagementService = inject(CourseManagementService);
    private courseStorageService = inject(CourseStorageService);
    private route = inject(ActivatedRoute);
    private changeDetectorRef = inject(ChangeDetectorRef);
    private metisConversationService = inject(MetisConversationService);
    private router = inject(Router);
    private courseAccessStorageService = inject(CourseAccessStorageService);
    private profileService = inject(ProfileService);
    private ltiService = inject(LtiService);
    private featureToggleService = inject(FeatureToggleService);

    private ngUnsubscribe = new Subject<void>();
    private closeSidebarEventSubscription: Subscription;
    private openSidebarEventSubscription: Subscription;
    private subscription: Subscription;
    dashboardSubscription: Subscription;
    profileSubscription?: Subscription;
    private ltiSubscription: Subscription;
    private controlsSubscription?: Subscription;
    private vcSubscription?: Subscription;
    private eventSubscriber: Subscription;
    private courseSub?: Subscription;

    // Signals for reactive state management
    private courseId = signal<number | undefined>(undefined);
    course = signal<Course | undefined>(undefined);
    courses = signal<Course[]>([]);
    refreshingCourse = signal<boolean>(false);
    hasUnreadMessages = signal<boolean>(false);
    communicationRouteLoaded = signal<boolean>(false);
    atlasEnabled = signal<boolean>(false);
    isProduction = signal<boolean>(true);
    isTestServer = signal<boolean>(false);
    pageTitle = signal<string>('');
    isNavbarCollapsed = signal<boolean>(false);
    isSidebarCollapsed = signal<boolean>(false);
    isExamStarted = signal<boolean>(false);
    isShownViaLti = signal<boolean>(false);
    hasSidebar = signal<boolean>(false);
    localCIActive = signal<boolean>(false);
    irisEnabled = signal<boolean>(false);
    ltiEnabled = signal<boolean>(false);

    sidebarItems = computed(() => this.getSidebarItems());

    readonly MIN_DISPLAYED_COURSES: number = 6;

    private conversationServiceInstantiated = signal<boolean>(false);
    private checkedForUnreadMessages = signal<boolean>(false);
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
    >(new CourseDetailComponent());

    // Rendered embedded view for controls in the bar so we can destroy it if needed
    private controlsEmbeddedView?: EmbeddedViewRef<any>;
    // The current controls template from the sub-route component to render
    private controls = signal<TemplateRef<any> | undefined>(undefined);
    // The current controls configuration from the sub-route component
    public controlConfiguration = signal<BarControlConfiguration | undefined>(undefined);

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
    faChevronLeft = faChevronLeft;
    facSidebar = facSidebar;
    faQuestion = faQuestion;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);
    private featureToggleSub: Subscription;

    constructor() {
        effect(() => {
            if (this.controlConfiguration() && this.controls() && this.controlsViewContainer) {
                this.tryRenderControls();
            }
        });

        effect(() => {
            if (this.conversationServiceInstantiated() && this.communicationRouteLoaded()) {
                this.setUpConversationService();
            }
        });
    }

    async ngOnInit() {
        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.isSidebarCollapsed.set(true);
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.isSidebarCollapsed.set(false);
        });

        this.subscription = this.route.params.subscribe((params: { courseId: string }) => {
            this.courseId.set(Number(params.courseId));
            this.subscribeToCourseUpdates(Number(params.courseId));
        });

        // Subscribe to course modifications and reload the course after a change.
        this.eventSubscriber = this.eventManager.subscribe('courseModification', () => {
            this.subscribeToCourseUpdates(this.courseId()!);
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isProduction.set(profileInfo?.inProduction);
                this.isTestServer.set(profileInfo.testServer ?? false);
                this.atlasEnabled.set(profileInfo.activeProfiles.includes(PROFILE_ATLAS));
                this.irisEnabled.set(profileInfo.activeProfiles.includes(PROFILE_IRIS));
                this.ltiEnabled.set(profileInfo.activeProfiles.includes(PROFILE_LTI));
                this.localCIActive.set(profileInfo?.activeProfiles.includes(PROFILE_LOCALCI));
            }
        });

        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo: any) => {
            this.isProduction.set(profileInfo?.inProduction);
            this.isTestServer.set(profileInfo.testServer ?? false);
            this.atlasEnabled.set(profileInfo.activeProfiles.includes(PROFILE_ATLAS));
        });

        this.getCollapseStateFromStorage();
        const storedCourse = this.courseStorageService.getCourse(this.courseId() || 0);
        this.course.set(storedCourse);

        // Notify the course access storage service that the course has been accessed
        if (storedCourse) {
            this.courseAccessStorageService.onCourseAccessed(
                this.courseId() || 0,
                CourseAccessStorageService.STORAGE_KEY,
                CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
            );
            this.courseAccessStorageService.onCourseAccessed(
                this.courseId() || 0,
                CourseAccessStorageService.STORAGE_KEY_DROPDOWN,
                CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_DROPDOWN,
            );
        }

        await firstValueFrom(this.loadCourse());
        await this.updateRecentlyAccessedCourses();

        this.ltiSubscription = this.ltiService.isShownViaLti$.subscribe((isShownViaLti: boolean) => {
            this.isShownViaLti.set(isShownViaLti);
        });
    }

    private subscribeToCourseUpdates(courseId: number) {
        this.courseSub = this.courseManagementService.find(courseId).subscribe((courseResponse) => {
            this.course.set(courseResponse.body!);
        });
    }

    loadCourse(): Observable<void> {
        return this.courseManagementService.findOneForDashboard(this.courseId()!).pipe(
            map((res: HttpResponse<Course>) => {
                if (res.body) {
                    this.course.set(res.body);
                }

                this.setUpConversationService();
            }),
        );
    }

    /** initialize courses attribute by retrieving all courses from the server */
    async updateRecentlyAccessedCourses() {
        this.dashboardSubscription = this.courseManagementService.findAllForDropdown().subscribe({
            next: (res: HttpResponse<Course[]>) => {
                if (res.body) {
                    let courses: Course[] = [];
                    res.body?.forEach((course) => {
                        courses.push(course);
                    });
                    courses = sortCourses(courses);
                    if (courses.length > this.MIN_DISPLAYED_COURSES) {
                        const lastAccessedCourseIds = this.courseAccessStorageService.getLastAccessedCourses(CourseAccessStorageService.STORAGE_KEY_DROPDOWN);
                        courses = courses.filter((course) => lastAccessedCourseIds.includes(course.id!));
                    }
                    courses = courses.filter((course) => course.id !== this.courseId());
                    this.courses.set(courses);
                }
            },
        });
    }

    /** Navigate to a new Course */
    switchCourse(course: Course) {
        this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
            this.router.navigate(['course-management', course.id, 'exercises']);
        });
    }

    private setUpConversationService() {
        const currentCourse = this.course();
        if (!currentCourse || (!isMessagingEnabled(currentCourse) && !isCommunicationEnabled(currentCourse))) {
            return;
        }

        if (!this.conversationServiceInstantiated() && this.communicationRouteLoaded()) {
            this.metisConversationService
                .setUpConversationService(currentCourse)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    complete: () => {
                        this.conversationServiceInstantiated.set(true);
                        // service is fully set up, now we can subscribe to the respective observables
                        this.subscribeToHasUnreadMessages();
                    },
                });
        } else if (!this.checkedForUnreadMessages() && isMessagingEnabled(currentCourse)) {
            this.metisConversationService.checkForUnreadMessages(currentCourse);
            this.subscribeToHasUnreadMessages();
            this.checkedForUnreadMessages.set(true);
        }
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
        this.metisConversationService.hasUnreadMessages$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((hasUnreadMessages: boolean) => {
            this.hasUnreadMessages.set(hasUnreadMessages ?? false);
        });
    }

    @HostListener('window:keydown.Control.Shift.b', ['$event'])
    onKeyDownControlShiftB(event: KeyboardEvent) {
        event.preventDefault();
        this.toggleSidebar();
    }

    /**
     * Accepts a component reference of the subcomponent rendered based on the current route.
     * If it provides a controlsConfiguration, we try to render the controls component
     * @param componentRef the sub route component that has been mounted into the router outlet
     */
    onSubRouteActivate(componentRef: any) {
        this.getPageTitle();
        //console.log('this.route.snapshot', this.route.snapshot);
        const urlSegments = this.router.url.split('/');
        //console.log('urlSegments', urlSegments);
        // this is intentional. The url segments are "", "course-management", "courseId", "communication"
        this.communicationRouteLoaded.set(urlSegments.length > 3 && urlSegments[3] === 'communication');
        this.hasSidebar.set(this.getHasSidebar());

        this.setUpConversationService();

        if (componentRef.controlConfiguration) {
            const provider = componentRef as BarControlConfigurationProvider;
            this.controlConfiguration.set(provider.controlConfiguration as BarControlConfiguration);

            this.controlsSubscription = provider.controlConfiguration?.subject?.subscribe(async (controls: TemplateRef<any>) => {
                this.controls.set(controls);
                this.tryRenderControls();
                await firstValueFrom(provider.controlsRendered);
                this.tryRenderControls();
            });
        }

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

        // Since we change the pageTitle + might be pulling data upwards during a render cycle, we need to re-run change detection
        this.changeDetectorRef.detectChanges();
    }

    getPageTitle(): void {
        // Get the most deeply active route
        let activeRoute = this.route.snapshot;
        while (activeRoute.firstChild) {
            activeRoute = activeRoute.firstChild;
        }

        // Now get the page title from the most deeply active route
        const routePageTitle: string = activeRoute.data?.pageTitle;

        if (routePageTitle) {
            this.pageTitle.set(routePageTitle);
        } else {
            // Fallback if no page title found
            this.pageTitle.set('');
        }
    }

    /**
     * Removes the controls component from the DOM and cancels the listener for controls changes.
     * Called by the router outlet as soon as the currently mounted component is removed
     */
    onSubRouteDeactivate() {
        this.removeCurrentControlsView();
        this.controls.set(undefined);
        this.controlConfiguration.set(undefined);
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
        if (this.controlConfiguration() && this.controls() && this.controlsViewContainer) {
            this.removeCurrentControlsView();
            this.controlsEmbeddedView = this.controlsViewContainer.createEmbeddedView(this.controls()!);
            this.controlsEmbeddedView.detectChanges();
        }
    }

    ngOnDestroy() {
        this.controlsSubscription?.unsubscribe();
        this.vcSubscription?.unsubscribe();
        this.subscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.dashboardSubscription?.unsubscribe();
        this.closeSidebarEventSubscription?.unsubscribe();
        this.openSidebarEventSubscription?.unsubscribe();
        this.ltiSubscription?.unsubscribe();
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.eventManager.destroy(this.eventSubscriber);
        this.courseSub?.unsubscribe();
        this.featureToggleSub?.unsubscribe();
    }

    @HostListener('window:keydown.Control.m', ['$event'])
    onKeyDownControlM(event: KeyboardEvent) {
        event.preventDefault();
        this.toggleCollapseState();
    }

    getCollapseStateFromStorage() {
        const storedCollapseState: string | null = localStorage.getItem('navbar.collapseState');
        if (storedCollapseState) this.isNavbarCollapsed.set(JSON.parse(storedCollapseState));
    }

    toggleCollapseState() {
        this.isNavbarCollapsed.update((value) => !value);
        localStorage.setItem('navbar.collapseState', JSON.stringify(this.isNavbarCollapsed()));
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

    getSidebarItems(): SidebarItem[] {
        const sidebarItems: SidebarItem[] = [];
        const currentCourse = this.course();

        sidebarItems.push(...this.getDefaultItems());
        if (currentCourse?.isAtLeastEditor) {
            sidebarItems.splice(3, 0, this.getLecturesItems());
        }

        if (currentCourse?.isAtLeastInstructor && this.irisEnabled()) {
            const irisSettingsItem: SidebarItem = {
                routerLink: 'iris-settings',
                icon: faRobot,
                title: 'IRIS Settings',
                translation: 'artemisApp.iris.settings.button.course.title',
                testId: 'iris-settings',
                hidden: false,
            };
            sidebarItems.push(irisSettingsItem);
        }

        // Communication - only when communication is enabled
        if (currentCourse && isCommunicationEnabled(currentCourse)) {
            sidebarItems.push(this.getCommunicationsItems());
        }

        // Tutorial Groups - when configuration exists or user is instructor
        if (currentCourse?.tutorialGroupsConfiguration || currentCourse?.isAtLeastInstructor) {
            const tutorialGroupsItem = this.getTutorialGroupsItems();
            sidebarItems.push(tutorialGroupsItem);
        }

        // Competency Management - only for instructors with Atlas enabled
        if (currentCourse?.isAtLeastInstructor && this.atlasEnabled()) {
            sidebarItems.push(this.getCompetenciesItems());
        }

        // Learning Path - only for instructors with Atlas enabled and learning paths enabled
        if (currentCourse?.isAtLeastInstructor && this.atlasEnabled()) {
            this.featureToggleSub = this.featureToggleService.getFeatureToggleActive(FeatureToggle.LearningPaths).subscribe((isActive) => {
                if (isActive) {
                    sidebarItems.push(this.getLearningPathItems());
                }
            });
        }

        const assessmentDashboardItem: SidebarItem = {
            routerLink: 'assessment-dashboard',
            icon: faUserCheck,
            title: 'Assessment Dashboard',
            translation: 'entity.action.assessmentDashboard',
            hidden: false,
        };
        sidebarItems.push(assessmentDashboardItem);

        if (currentCourse?.isAtLeastInstructor) {
            const scoresItem: SidebarItem = {
                routerLink: 'scores',
                icon: faTable,
                title: 'Scores',
                translation: 'entity.action.scores',
                hidden: false,
            };
            sidebarItems.push(scoresItem);
        }

        if (currentCourse?.isAtLeastTutor && currentCourse?.faqEnabled) {
            sidebarItems.push(this.getFaqItem());
        }

        if (currentCourse?.isAtLeastInstructor && this.localCIActive()) {
            const buildQueueItem: SidebarItem = {
                routerLink: 'build-queue',
                icon: faList,
                title: 'Build Queue',
                translation: 'artemisApp.buildQueue.title',
                hidden: false,
            };
            sidebarItems.push(buildQueueItem);
        }

        // LTI Configuration - only for instructors with LTI enabled and online course
        if (this.ltiEnabled() && currentCourse?.onlineCourse && currentCourse?.isAtLeastInstructor) {
            const ltiConfigItem: SidebarItem = {
                routerLink: 'lti-configuration',
                icon: faPuzzlePiece,
                title: 'LTI Configuration',
                translation: 'global.menu.admin.lti',
                testId: 'lti-settings',
                hidden: false,
            };
            sidebarItems.push(ltiConfigItem);
        }

        return sidebarItems;
    }

    getLecturesItems() {
        const lecturesItem: SidebarItem = {
            routerLink: 'lectures',
            icon: faChalkboardUser,
            title: 'Lectures',
            translation: 'artemisApp.courseOverview.menu.lectures',
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
            featureToggle: FeatureToggle.TutorialGroups,
            hidden: false,
        };
        return tutorialGroupsItem;
    }

    getCompetenciesItems() {
        const competenciesItem: SidebarItem = {
            routerLink: 'competency-management',
            icon: faFlag,
            title: 'Competency Management',
            translation: 'artemisApp.courseOverview.menu.competencies',
            hidden: false,
        };
        return competenciesItem;
    }

    getLearningPathItems() {
        const learningPathItem: SidebarItem = {
            routerLink: 'learning-path-management',
            icon: faNetworkWired,
            title: 'Learning Path',
            translation: 'artemisApp.courseOverview.menu.learningPath',
            featureToggle: FeatureToggle.LearningPaths,
            hidden: false,
        };
        return learningPathItem;
    }

    getFaqItem() {
        const faqItem: SidebarItem = {
            routerLink: 'faqs',
            icon: faQuestion,
            title: 'FAQs',
            translation: 'artemisApp.courseOverview.menu.faq',
            hidden: false,
        };
        return faqItem;
    }

    getDefaultItems() {
        const items: SidebarItem[] = [];

        const overviewItem: SidebarItem = {
            routerLink: '.',
            icon: faTableCells,
            title: 'Overview',
            translation: 'artemisApp.course.overview',
            hidden: false,
            isPrefix: true,
        };
        const exercisesItem: SidebarItem = {
            routerLink: 'exercises',
            icon: faListAlt,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'course-statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            guidedTour: true,
            hidden: false,
        };

        return items.concat([overviewItem, this.getExamsItems(), exercisesItem, statisticsItem]);
    }

    // only the communication tab has a sidebar in the management view
    getHasSidebar(): boolean {
        return this.communicationRouteLoaded();
    }
    toggleSidebar() {
        if (!this.activatedComponentReference() || !(this.activatedComponentReference() instanceof CourseConversationsComponent)) {
            return;
        }
        const childRouteComponent = this.activatedComponentReference() as CourseConversationsComponent;
        childRouteComponent.toggleSidebar();
        this.isSidebarCollapsed.set(childRouteComponent.isCollapsed);
    }

    protected readonly ButtonSize = ButtonSize;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;
}
