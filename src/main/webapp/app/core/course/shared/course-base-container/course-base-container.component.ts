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
    effect,
    inject,
    signal,
} from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, firstValueFrom } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
import dayjs from 'dayjs/esm';

import { BarControlConfiguration } from 'app/shared/tab-bar/tab-bar';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LtiService } from 'app/shared/service/lti.service';
import { sortCourses } from 'app/shared/util/course.util';
import { SidebarItem } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { MODULE_FEATURE_ATLAS, PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { CourseAccessStorageService } from '../services/course-access-storage.service';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { toSignal } from '@angular/core/rxjs-interop';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

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
    protected metisConversationService = inject(MetisConversationService);
    protected router = inject(Router);
    protected courseAccessStorageService = inject(CourseAccessStorageService);
    protected profileService = inject(ProfileService);
    protected ltiService = inject(LtiService);
    protected courseSidebarService = inject(CourseSidebarService);
    protected localStorageService = inject(LocalStorageService);

    ngUnsubscribe = new Subject<void>();
    protected closeSidebarEventSubscription: Subscription;
    protected openSidebarEventSubscription: Subscription;
    protected subscription?: Subscription;
    protected ltiSubscription: Subscription;
    protected loadCourseSubscription?: Subscription;
    dashboardSubscription: Subscription;

    courseId = signal<number>(0);
    course = signal<Course | undefined>(undefined);
    courses = signal<Course[] | undefined>(undefined);
    refreshingCourse = signal<boolean>(false);
    hasUnreadMessages = signal<boolean>(false);
    communicationRouteLoaded = signal<boolean>(false);

    atlasEnabled: boolean = false;
    irisEnabled: boolean = false;
    ltiEnabled: boolean = false;
    localCIActive: boolean = false;
    isProduction: boolean = true;
    isTestServer: boolean = false;

    pageTitle = signal<string>('');
    isNavbarCollapsed = signal<boolean>(false);
    isSidebarCollapsed = signal<boolean>(false);
    isExamStarted = signal<boolean>(false);
    isShownViaLti = signal<boolean>(false);
    hasSidebar = signal<boolean>(false);

    private navigationEnd$ = this.router.events.pipe(filter((event) => event instanceof NavigationEnd));

    // Create a signal from the observable
    readonly navigationEnd = toSignal(this.navigationEnd$);

    sidebarItems = signal<SidebarItem[]>([]);
    readonly MIN_DISPLAYED_COURSES: number = 6;

    conversationServiceInstantiated = signal<boolean>(false);
    checkedForUnreadMessages = signal<boolean>(false);

    protected controlsEmbeddedView?: EmbeddedViewRef<any>;
    controls = signal<TemplateRef<any> | undefined>(undefined);
    public controlConfiguration = signal<BarControlConfiguration | undefined>(undefined);
    protected controlsSubscription?: Subscription;
    protected vcSubscription?: Subscription;

    @ViewChild('controlsViewContainer', { read: ViewContainerRef }) controlsViewContainer: ViewContainerRef;
    @ViewChildren('controlsViewContainer') controlsViewContainerAsList: QueryList<ViewContainerRef>;

    protected readonly FeatureToggle = FeatureToggle;

    constructor() {
        effect(() => {
            const navEvent = this.navigationEnd();

            if (navEvent) {
                this.handleNavigationEndActions();
            }
        });

        effect(() => {
            const updatedCourse = this.course();
            const communicationRouteLoaded = this.communicationRouteLoaded();
            if (isCommunicationEnabled(updatedCourse) && communicationRouteLoaded) {
                this.setupConversationService();
            } else if (!isCommunicationEnabled(updatedCourse) && this.conversationServiceInstantiated()) {
                this.disableConversationService();
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

        this.isProduction = this.profileService.isProduction();
        this.isTestServer = this.profileService.isTestServer();
        this.atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
        this.irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
        this.ltiEnabled = this.profileService.isProfileActive(PROFILE_LTI);
        this.localCIActive = this.profileService.isProfileActive(PROFILE_LOCALCI);

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

    abstract handleCourseIdChange(courseId: number): void;

    protected abstract handleNavigationEndActions(): void;

    protected abstract getSidebarItems(): SidebarItem[];

    protected abstract getHasSidebar(): boolean;

    protected abstract handleComponentActivation(componentRef: any): void;

    abstract handleToggleSidebar(): void;

    abstract loadCourse(refresh?: boolean): Observable<void>;
    abstract switchCourse(course: Course): void;

    ngAfterViewInit() {
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
        this.closeSidebarEventSubscription?.unsubscribe();
        this.openSidebarEventSubscription?.unsubscribe();
        this.ltiSubscription?.unsubscribe();
        this.loadCourseSubscription?.unsubscribe();
        this.dashboardSubscription?.unsubscribe();
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    private disableConversationService() {
        this.conversationServiceInstantiated.set(false);
        this.metisConversationService.disableConversationService();
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
    onKeyDownControlShiftB(event: Event) {
        event.preventDefault();
        this.toggleSidebar();
    }

    toggleSidebar() {
        this.handleToggleSidebar();
    }

    getPageTitle(): void {
        let activeRoute = this.route.snapshot;
        while (activeRoute.firstChild) {
            activeRoute = activeRoute.firstChild;
        }

        const routePageTitle: string = activeRoute.data?.pageTitle;

        if (routePageTitle) {
            this.pageTitle.set(routePageTitle);
        } else {
            this.pageTitle.set('');
        }
    }

    @HostListener('window:keydown.Control.m', ['$event'])
    onKeyDownControlM(event: Event) {
        event.preventDefault();
        this.toggleCollapseState();
    }

    getCollapseStateFromStorage() {
        const storedCollapseState: boolean | undefined = this.localStorageService.retrieve<boolean>('navbar.collapseState');
        if (storedCollapseState !== undefined) this.isNavbarCollapsed.set(storedCollapseState);
    }

    toggleCollapseState() {
        this.isNavbarCollapsed.update((value) => !value);
        this.localStorageService.store<boolean>('navbar.collapseState', this.isNavbarCollapsed());
    }

    /**
     * A course is active if the end date is after the current date or
     * end date is not set at all
     */
    isCourseActive(course: Course): boolean {
        return course.endDate ? dayjs(course.endDate).isAfter(dayjs()) : true;
    }
}
