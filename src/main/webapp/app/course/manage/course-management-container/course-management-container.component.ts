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
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subject, Subscription, firstValueFrom } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
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
    faListAlt,
    faNetworkWired,
    faPersonChalkboard,
    faQuestion,
    faSync,
    faTable,
    faTimes,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { facSidebar } from 'app/icons/icons';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { LtiService } from 'app/shared/service/lti.service';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';
import { PROFILE_ATLAS } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseActionItem, CourseSidebarComponent, SidebarItem } from 'app/overview/course-sidebar/course-sidebar.component';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { TutorialGroupsChecklistComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CompetencyManagementComponent } from 'app/course/competencies/competency-management/competency-management.component';
import { LearningPathInstructorPageComponent } from 'app/course/learning-paths/pages/learning-path-instructor-page/learning-path-instructor-page.component';
import { AssessmentDashboardComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.component';
import { CourseScoresComponent } from 'app/course/course-scores/course-scores.component';
import { FaqComponent } from 'app/faq/faq.component';
import { BuildQueueComponent } from 'app/localci/build-queue/build-queue.component';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { sortCourses } from 'app/shared/util/course.util';

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
    ],
})
export class CourseManagementContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    private courseService = inject(CourseManagementService);
    private courseStorageService = inject(CourseStorageService);
    private route = inject(ActivatedRoute);
    private changeDetectorRef = inject(ChangeDetectorRef);
    private metisConversationService = inject(MetisConversationService);
    private router = inject(Router);
    private courseAccessStorageService = inject(CourseAccessStorageService);
    private profileService = inject(ProfileService);
    private ltiService = inject(LtiService);

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
    hasUnreadMessages: boolean;
    communicationRouteLoaded: boolean;
    atlasEnabled = false;
    isProduction = true;
    isTestServer = false;
    pageTitle: string;
    hasSidebar = false;
    isNavbarCollapsed = false;
    isSidebarCollapsed = false;
    profileSubscription?: Subscription;
    showRefreshButton = false;
    isExamStarted = false;
    private examStartedSubscription: Subscription;
    readonly MIN_DISPLAYED_COURSES: number = 6;
    isShownViaLti = false;
    private ltiSubscription: Subscription;

    private conversationServiceInstantiated = false;
    private checkedForUnreadMessages = false;
    activatedComponentReference:
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
        | BuildQueueComponent;

    // Rendered embedded view for controls in the bar so we can destroy it if needed
    private controlsEmbeddedView?: EmbeddedViewRef<any>;
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
    faChevronLeft = faChevronLeft;
    facSidebar = facSidebar;
    faQuestion = faQuestion;

    FeatureToggle = FeatureToggle;
    CachingStrategy = CachingStrategy;

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);

    async ngOnInit() {
        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.isSidebarCollapsed = true;
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.isSidebarCollapsed = false;
        });

        this.subscription = this.route.params.subscribe((params: { courseId: string }) => {
            this.courseId = Number(params.courseId);
        });

        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo: any) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
            this.atlasEnabled = profileInfo.activeProfiles.includes(PROFILE_ATLAS);
        });

        this.getCollapseStateFromStorage();
        this.course = this.courseStorageService.getCourse(this.courseId);
        // Notify the course access storage service that the course has been accessed
        // If course is not active, it means that it is accessed from course archive, which should not
        // be stored in local storage and therefore displayed in recently accessed
        if (this.course) {
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

        await this.updateRecentlyAccessedCourses();
        this.ltiSubscription = this.ltiService.isShownViaLti$.subscribe((isShownViaLti: boolean) => {
            this.isShownViaLti = isShownViaLti;
        });
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
            this.router.navigate(['course-management', course.id, 'exercises']);
        });
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

    courseActionItemClick(item?: CourseActionItem) {
        if (item?.action) {
            item.action(item);
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

        this.setUpConversationService();

        this.hasSidebar = this.getHasSidebar();

        if (componentRef.controlConfiguration) {
            const provider = componentRef as BarControlConfigurationProvider;
            this.controlConfiguration = provider.controlConfiguration as BarControlConfiguration;
            this.controlsSubscription =
                this.controlConfiguration.subject?.subscribe(async (controls: TemplateRef<any>) => {
                    this.controls = controls;
                    this.tryRenderControls();
                    await firstValueFrom(provider.controlsRendered);
                    this.tryRenderControls();
                }) || undefined;
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
            this.activatedComponentReference = componentRef;
        }

        // Since we change the pageTitle + might be pulling data upwards during a render cycle, we need to re-run change detection
        this.changeDetectorRef.detectChanges();
    }

    // toggleSidebar() {
    //     if (!this.activatedComponentReference) {
    //         return;
    //     }
    //     const childRouteComponent = this.activatedComponentReference;
    //     childRouteComponent.toggleSidebar();
    //     this.isSidebarCollapsed = childRouteComponent.isCollapsed;
    // }

    getPageTitle(): void {
        const routePageTitle: string = this.route.snapshot.firstChild?.data?.pageTitle;
        this.pageTitle = routePageTitle?.substring(routePageTitle.indexOf('.') + 1);
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

    ngOnDestroy() {
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

    getSidebarItems(): SidebarItem[] {
        const sidebarItems = this.getDefaultItems();
        const lecturesItem: SidebarItem = this.getLecturesItems();
        sidebarItems.splice(-1, 0, lecturesItem);
        const examsItem: SidebarItem = this.getExamsItems();
        sidebarItems.unshift(examsItem);
        if (isCommunicationEnabled(this.course)) {
            const communicationsItem: SidebarItem = this.getCommunicationsItems();
            sidebarItems.push(communicationsItem);
        }

        //if (this.hasTutorialGroups()) {
        const tutorialGroupsItem: SidebarItem = this.getTutorialGroupsItems();
        sidebarItems.push(tutorialGroupsItem);
        //}

        if (this.atlasEnabled) {
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
            icon: faListAlt,
            title: 'Exercises',
            translation: 'artemisApp.courseOverview.menu.exercises',
            hidden: false,
        };

        const statisticsItem: SidebarItem = {
            routerLink: 'statistics',
            icon: faChartColumn,
            title: 'Statistics',
            translation: 'artemisApp.courseOverview.menu.statistics',
            hasInOrionProperty: true,
            showInOrionWindow: false,
            guidedTour: true,
            hidden: false,
        };

        return items.concat([exercisesItem, statisticsItem]);
    }
}
