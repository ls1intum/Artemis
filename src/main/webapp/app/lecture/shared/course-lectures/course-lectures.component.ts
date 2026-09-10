import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { Subscription, distinctUntilChanged, forkJoin, map } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseOverviewService, SidebarLecture } from 'app/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { LtiService } from 'app/foundation/service/lti.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { SidebarView } from 'app/course/shared/sidebar-view.interface';
import { CourseOverviewTabDataService } from 'app/course/overview/services/course-overview-tab-data.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    dueSoon: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    future: true,
    current: false,
    dueSoon: true,
    past: true,
    noDate: true,
};

const DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
    future: false,
    current: false,
    dueSoon: false,
    past: false,
    noDate: false,
};

@Component({
    selector: 'jhi-course-lectures',
    templateUrl: './course-lectures.component.html',
    styleUrls: ['../../../course/overview/course-overview/course-overview.scss'],
    imports: [SidebarComponent, CourseSidebarToggleButtonComponent, RouterOutlet, TranslateDirective],
})
export class CourseLecturesComponent implements OnInit, OnDestroy, SidebarView {
    private courseStorageService = inject(CourseStorageService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private courseOverviewService = inject(CourseOverviewService);
    private ltiService = inject(LtiService);
    private lectureService = inject(LectureService);
    private sessionStorageService = inject(SessionStorageService);
    private courseOverviewTabDataService = inject(CourseOverviewTabDataService);
    private courseTabRefreshService = inject(CourseTabRefreshService);
    private tabReselectionSubscription?: Subscription;

    private parentParamSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private lecturesSubscription?: Subscription;
    private multiLaunchSubscription?: Subscription;
    private multiLaunchLecturesSubscription?: Subscription;
    private queryParamsSubscription?: Subscription;

    readonly course = signal<Course | undefined>(undefined);
    readonly courseId = signal<number>(undefined!);

    /** The lectures of the course, loaded by this tab rather than shipped with the course. */
    readonly lectures = signal<SidebarLecture[] | undefined>(undefined);
    readonly lectureSelected = signal(true);
    readonly sidebarData = signal<SidebarData | undefined>(undefined);
    accordionLectureGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sortedLectures: SidebarLecture[] = [];
    sidebarLectures: SidebarCardElement[] = [];
    readonly isCollapsed = signal(false);
    readonly pageTitle = signal<string>('');
    isMultiLaunch = false;
    multiLaunchLectureIDs: number[] = [];
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    private readonly activeLectureDetails = signal<CourseLectureDetailsComponent | undefined>(undefined);
    protected readonly activeLectureDetailsSidebarSync = effect(() => this.activeLectureDetails()?.setSidebarToggle(this.isCollapsed(), () => this.toggleSidebar()));

    ngOnInit() {
        this.isCollapsed.set(this.courseOverviewService.getSidebarCollapseStateFromStorage('lecture'));
        this.queryParamsSubscription = this.route.queryParams.subscribe((params) => {
            this.multiLaunchLectureIDs = params['lectureIDs'] ? params['lectureIDs'].split(',').map((id: string) => Number(id)) : [];
        });

        this.multiLaunchSubscription = this.ltiService.isMultiLaunch$.subscribe((isMultiLaunch) => {
            this.isMultiLaunch = isMultiLaunch;
        });

        this.parentParamSubscription = this.route
            .parent!.params.pipe(
                map((params) => Number(params.courseId)),
                distinctUntilChanged(),
            )
            .subscribe((courseId) => this.activateCourse(courseId));
    }

    /** Cancels the previous course's streams and starts this routed tab for the newly active course. */
    private activateCourse(courseId: number): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.lecturesSubscription?.unsubscribe();
        this.multiLaunchLecturesSubscription?.unsubscribe();
        // Switching course reuses this component instance, so the previous course's re-selection stream has to go with
        // it. Leaving it subscribed made every later tab re-selection load the lectures once per course visited.
        this.tabReselectionSubscription?.unsubscribe();

        this.courseId.set(courseId);
        this.course.set(this.courseStorageService.getCourse(courseId));
        this.lectures.set(undefined);
        this.lectureSelected.set(true);
        this.sidebarData.set(undefined);
        this.accordionLectureGroups = DEFAULT_UNIT_GROUPS;
        this.sortedLectures = [];
        this.sidebarLectures = [];

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(courseId).subscribe((course: Course) => {
            this.course.set(course);
        });
        this.loadLectures();
        this.tabReselectionSubscription = this.courseTabRefreshService.reselections(this.route).subscribe(() => this.loadLectures());
    }

    /**
     * Loads the lectures of this course. They used to arrive as part of the (expensive) course load, which made every
     * course visit pay for them even when the lectures tab was never opened. The redirect to the last selected or
     * upcoming lecture has to wait for them, so it runs once they arrive.
     */
    /**
     * Selecting the tab that is already open acts as a refresh: the router reports it as a fresh navigation, the
     * navigation-scoped hold in the data service therefore misses, and the request reaches the server. The displayed
     * data is left untouched until the response arrives, so a refresh does not flash an empty tab.
     */
    private loadLectures(): void {
        this.lecturesSubscription?.unsubscribe();
        this.lecturesSubscription = this.courseOverviewTabDataService.loadLecturesIfNeeded(this.courseId()).subscribe({
            next: (lectures) => {
                this.lectures.set(lectures);
                this.prepareSidebarData();
                // If no lecture is selected navigate to the lastSelected or upcoming lecture
                this.navigateToLecture();
            },
            error: () => {
                this.lectures.set([]);
                this.prepareSidebarData();
            },
        });
    }

    navigateToLecture() {
        const upcomingLecture = this.courseOverviewService.getUpcomingLecture(this.lectures());
        const lastSelectedLecture = this.getLastSelectedLecture();
        const lectureId = this.route.firstChild?.snapshot?.params?.lectureId;
        if (!lectureId && lastSelectedLecture) {
            void this.router.navigate([lastSelectedLecture], { relativeTo: this.route, replaceUrl: true });
        } else if (!lectureId && upcomingLecture) {
            void this.router.navigate([upcomingLecture.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            this.lectureSelected.set(!!lectureId);
        }
    }

    prepareSidebarData() {
        const lectures: SidebarLecture[] = [];

        if (this.multiLaunchLectureIDs?.length > 0) {
            const lectureObservables = this.multiLaunchLectureIDs.map((lectureId) => this.lectureService.find(lectureId));

            this.multiLaunchLecturesSubscription?.unsubscribe();
            this.multiLaunchLecturesSubscription = forkJoin(lectureObservables).subscribe((lectureResponses) => {
                lectureResponses.forEach((response) => {
                    lectures.push(response.body!);
                });
                this.processLectures(lectures);
            });
        } else {
            this.multiLaunchLecturesSubscription?.unsubscribe();
            const lectures = this.lectures();
            if (!lectures) {
                return;
            }
            this.processLectures(lectures);
        }
    }

    processLectures(lectures: SidebarLecture[]) {
        const filteredLectures = lectures.filter((lecture) => !lecture.isTutorialLecture);
        this.sortedLectures = this.courseOverviewService.sortLectures(filteredLectures);
        this.sidebarLectures = this.courseOverviewService.mapLecturesToSidebarCardElements(this.sortedLectures);
        this.accordionLectureGroups = this.courseOverviewService.groupLecturesByStartDate(this.sortedLectures);
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData.set({
            groupByCategory: true,
            storageId: 'lecture',
            groupedData: this.accordionLectureGroups,
            ungroupedData: this.sidebarLectures,
        });
    }

    setPageTitle(pageTitle: string): void {
        this.pageTitle.set(pageTitle);
    }

    toggleSidebar() {
        this.isCollapsed.update((collapsed) => !collapsed);
        this.courseOverviewService.setSidebarCollapseState('lecture', this.isCollapsed());
    }

    getLastSelectedLecture(): string | undefined {
        return this.sessionStorageService.retrieve<string>('sidebar.lastSelectedItem.lecture.byCourse.' + this.courseId());
    }

    onSubRouteActivate(componentRef: unknown) {
        if (componentRef instanceof CourseLectureDetailsComponent) {
            this.activeLectureDetails.set(componentRef);
        }
    }

    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToLecture();
    }

    ngOnDestroy(): void {
        this.tabReselectionSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.lecturesSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
        this.multiLaunchSubscription?.unsubscribe();
        this.multiLaunchLecturesSubscription?.unsubscribe();
        this.queryParamsSubscription?.unsubscribe();
    }
}
