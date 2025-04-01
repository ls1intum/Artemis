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
    signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, firstValueFrom } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/core/shared/entities/course.model';
import { BarControlConfiguration } from 'app/shared/tab-bar/tab-bar';
import { CourseStorageService } from 'app/core/course/manage/course-storage.service';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { CourseAccessStorageService } from 'app/core/course/shared/course-access-storage.service';
import { CourseSidebarService } from 'app/core/course/overview/course-sidebar.service';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LtiService } from 'app/shared/service/lti.service';
import { sortCourses } from 'app/shared/util/course.util';
import { SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { PROFILE_ATLAS, PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';

/**
 * Base class that contains common functionality for course container components.
 * This abstract class is extended by CourseManagementContainerComponent and CourseOverviewComponent.
 */
@Component({
    template: '',
})
export abstract class BaseCourseContainerComponent implements OnInit, OnDestroy, AfterViewInit {
    protected courseManagementService = inject(CourseManagementService);
    protected courseStorageService = inject(CourseStorageService);
    protected route = inject(ActivatedRoute);
    protected changeDetectorRef = inject(ChangeDetectorRef);
    metisConversationService = inject(MetisConversationService);
    router = inject(Router);
    protected courseAccessStorageService = inject(CourseAccessStorageService);
    protected profileService = inject(ProfileService);
    protected ltiService = inject(LtiService);
    protected courseSidebarService = inject(CourseSidebarService);

    ngUnsubscribe = new Subject<void>();
    protected closeSidebarEventSubscription: Subscription;
    protected openSidebarEventSubscription: Subscription;
    protected subscription?: Subscription;
    protected profileSubscription?: Subscription;
    protected ltiSubscription: Subscription;
    protected loadCourseSubscription?: Subscription;
    dashboardSubscription: Subscription;

    // Common state properties using signals
    courseId = signal<number>(0);
    course = signal<Course | undefined>(undefined);
    courses = signal<Course[] | undefined>(undefined);
    refreshingCourse = signal<boolean>(false);
    hasUnreadMessages = signal<boolean>(false);
    communicationRouteLoaded = signal<boolean>(false);
    atlasEnabled = signal<boolean>(false);
    irisEnabled = signal<boolean>(false);
    ltiEnabled = signal<boolean>(false);
    localCIActive = signal<boolean>(false);
    isProduction = signal<boolean>(true);
    isTestServer = signal<boolean>(false);
    pageTitle = signal<string>('');
    isNavbarCollapsed = signal<boolean>(false);
    isSidebarCollapsed = signal<boolean>(false);
    isExamStarted = signal<boolean>(false);
    isShownViaLti = signal<boolean>(false);
    hasSidebar = signal<boolean>(false);

    sidebarItems = signal<SidebarItem[]>([]);
    readonly MIN_DISPLAYED_COURSES: number = 6;

    conversationServiceInstantiated = signal<boolean>(false);
    checkedForUnreadMessages = signal<boolean>(false);

    // Controls handling properties
    protected controlsEmbeddedView?: EmbeddedViewRef<any>;
    controls = signal<TemplateRef<any> | undefined>(undefined);
    public controlConfiguration = signal<BarControlConfiguration | undefined>(undefined);
    protected controlsSubscription?: Subscription;
    protected vcSubscription?: Subscription;

    @ViewChild('controlsViewContainer', { read: ViewContainerRef }) controlsViewContainer: ViewContainerRef;
    @ViewChildren('controlsViewContainer') controlsViewContainerAsList: QueryList<ViewContainerRef>;

    async ngOnInit() {
        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.isSidebarCollapsed.set(true);
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.isSidebarCollapsed.set(false);
        });

        this.profileSubscription = this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isProduction.set(profileInfo?.inProduction);
                this.isTestServer.set(profileInfo.testServer ?? false);
                this.atlasEnabled.set(profileInfo.activeProfiles.includes(PROFILE_ATLAS));
                this.irisEnabled.set(profileInfo.activeProfiles.includes(PROFILE_IRIS));
                this.ltiEnabled.set(profileInfo.activeProfiles.includes(PROFILE_LTI));
                this.localCIActive.set(profileInfo?.activeProfiles.includes(PROFILE_LOCALCI));
            }
        });

        this.getCollapseStateFromStorage();
        const storedCourse = this.courseStorageService.getCourse(this.courseId());

        this.course.set(storedCourse);

        // Notify the course access storage service that the course has been accessed
        if (storedCourse && this.isCourseActive(storedCourse)) {
            this.courseAccessStorageService.onCourseAccessed(
                this.courseId(),
                CourseAccessStorageService.STORAGE_KEY,
                CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
            );
            this.courseAccessStorageService.onCourseAccessed(
                this.courseId(),
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

    // Abstract methods to be implemented by child classes
    abstract handleCourseIdChange(courseId: number): void;

    protected abstract getSidebarItems(): SidebarItem[];

    protected abstract getHasSidebar(): boolean;

    protected abstract handleComponentActivation(componentRef: any): void;

    abstract handleToggleSidebar(): void;

    abstract loadCourse(refresh?: boolean): Observable<void>;
    abstract switchCourse(course: Course): void;

    ngAfterViewInit() {
        // Check if controls mount point is available, if not, wait for it
        if (this.controlsViewContainer) {
            this.tryRenderControls();
        } else {
            this.vcSubscription = this.controlsViewContainerAsList.changes.subscribe(() => this.tryRenderControls());
        }
    }

    ngOnDestroy() {
        this.controlsSubscription?.unsubscribe();
        this.vcSubscription?.unsubscribe();
        this.subscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.closeSidebarEventSubscription?.unsubscribe();
        this.openSidebarEventSubscription?.unsubscribe();
        this.ltiSubscription?.unsubscribe();
        this.loadCourseSubscription?.unsubscribe();
        this.dashboardSubscription?.unsubscribe();
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    /** Initialize courses attribute by retrieving all courses from the server */
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

    /**
     * Accepts a component reference of the subcomponent rendered based on the current route.
     * If it provides a controlsConfiguration, we try to render the controls component
     */
    onSubRouteActivate(componentRef: any) {
        this.getPageTitle();
        const urlSegments = this.router.url.split('?')[0].split('/');
        this.communicationRouteLoaded.set(urlSegments.length > 3 && urlSegments[3] === 'communication');

        this.hasSidebar.set(this.getHasSidebar());
        this.setupConversationService();

        if (componentRef.controlConfiguration) {
            const provider = componentRef;
            this.controlConfiguration.set(provider.controlConfiguration);

            this.controlsSubscription = provider.controlConfiguration?.subject?.subscribe(async (controls: TemplateRef<any>) => {
                this.controls.set(controls);
                this.tryRenderControls();
                await firstValueFrom(provider.controlsRendered);
                this.tryRenderControls();
            });
        }

        // Handle component specific activations in child classes
        this.handleComponentActivation(componentRef);

        // Since we change the pageTitle + might be pulling data upwards during a render cycle, we need to re-run change detection
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Removes the controls component from the DOM and cancels the listener for controls changes.
     */
    onSubRouteDeactivate() {
        this.removeCurrentControlsView();
        this.controls.set(undefined);
        this.controlConfiguration.set(undefined);
        this.controlsSubscription?.unsubscribe();
        this.changeDetectorRef.detectChanges();
    }

    private removeCurrentControlsView() {
        this.controlsEmbeddedView?.detach();
        this.controlsEmbeddedView?.destroy();
    }

    /**
     * Mounts the controls as specified by the currently mounted sub-route component to the ng-container in the top bar
     */
    tryRenderControls() {
        if (this.controlConfiguration() && this.controls() && this.controlsViewContainer) {
            this.removeCurrentControlsView();
            this.controlsEmbeddedView = this.controlsViewContainer.createEmbeddedView(this.controls()!);
            this.controlsEmbeddedView.detectChanges();
        }
    }

    setupConversationService() {
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

    protected subscribeToHasUnreadMessages() {
        this.metisConversationService.hasUnreadMessages$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((hasUnreadMessages: boolean) => {
            this.hasUnreadMessages.set(hasUnreadMessages ?? false);
        });
    }

    @HostListener('window:keydown.Control.Shift.b', ['$event'])
    onKeyDownControlShiftB(event: KeyboardEvent) {
        event.preventDefault();
        this.toggleSidebar();
    }

    toggleSidebar() {
        this.handleToggleSidebar();
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
     */
    isCourseActive(course: Course): boolean {
        return course.endDate ? dayjs(course.endDate).isAfter(dayjs()) : true;
    }
}
