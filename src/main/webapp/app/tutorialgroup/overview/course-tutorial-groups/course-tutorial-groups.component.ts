import { Component, Signal, computed, effect, inject, signal } from '@angular/core';
import { distinctUntilChanged } from 'rxjs/operators';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { filter, map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { CourseTutorialGroupDetailContainerComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail-container/course-tutorial-group-detail-container.component';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarData, SidebarItemShowAlways, TutorialGroupCategory } from 'app/foundation/types/sidebar';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import dayjs from 'dayjs/esm';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { convertTutorialGroupSummaryArrayDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { SidebarView } from 'app/course/shared/sidebar-view.interface';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    imports: [SidebarComponent, CourseSidebarToggleButtonComponent, RouterOutlet, TranslateDirective],
})
export class CourseTutorialGroupsComponent implements SidebarView {
    protected readonly DEFAULT_COLLAPSE_STATE: CollapseState = {
        allGroups: true,
        registeredGroups: false,
        furtherGroups: true,
        allTutorialLectures: true,
        currentTutorialLecture: false,
        furtherTutorialLectures: true,
    };
    protected readonly DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
        allGroups: false,
        registeredGroups: false,
        furtherGroups: false,
        allTutorialLectures: false,
        currentTutorialLecture: false,
        furtherTutorialLectures: false,
    };

    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private courseStorageService = inject(CourseStorageService);
    private tutorialGroupApiService = inject(TutorialGroupApi);
    private lectureService = inject(LectureService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private courseTabRefreshService = inject(CourseTabRefreshService);

    courseId = this.getCurrentCourseIdSignal();
    // Undefined until loaded, so a refresh that legitimately returns nothing is distinguishable from the initial state
    tutorialGroups = signal<TutorialGroup[] | undefined>(undefined);
    tutorialLectures = signal<Lecture[] | undefined>(undefined);
    sidebarData = signal<SidebarData | undefined>(undefined);
    itemSelected = this.getItemSelectedSignal();
    readonly isCollapsed = signal(false);
    readonly pageTitle = signal<string>('');
    currentTutorialLectureId = computed(() => this.computeCurrentTutorialLectureId());

    private readonly activeDetail = signal<CourseTutorialGroupDetailContainerComponent | CourseLectureDetailsComponent | undefined>(undefined);
    protected readonly activeDetailSidebarSync = effect(() => this.activeDetail()?.setSidebarToggle(this.isCollapsed(), () => this.toggleSidebar()));

    constructor() {
        this.isCollapsed.set(this.courseOverviewService.getSidebarCollapseStateFromStorage('tutorialGroup'));

        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.setTutorialGroupsAndTutorialLectures(courseId);
            }
        });

        // Selecting this tab while already on it acts as a refresh. It goes to the loaders directly rather than through
        // setTutorialGroupsAndTutorialLectures, which prefers whatever the stored course already holds and would
        // therefore make the refresh a no-op.
        this.courseTabRefreshService
            .reselections(this.activatedRoute)
            .pipe(takeUntilDestroyed())
            .subscribe(() => {
                const courseId = this.courseId();
                if (courseId) {
                    this.loadAndSetTutorialGroups(courseId);
                    this.loadAndSetTutorialLectures(courseId);
                }
            });

        effect(() => {
            const tutorialGroups = this.tutorialGroups();
            const tutorialLectures = this.tutorialLectures();
            // Rebuild as soon as either side has loaded, empty result included. Skipping the rebuild when both came
            // back empty left the previous course's — or a since-deleted group's — cards on screen after a refresh.
            if (tutorialGroups === undefined && tutorialLectures === undefined) {
                return;
            }
            this.prepareSidebarData(tutorialGroups ?? [], tutorialLectures ?? []);
            this.autoNavigateToLastSelectedOrUpcomingTutorialGroup(tutorialGroups ?? []);
        });

        effect(() => {
            this.lectureService.currentTutorialLectureId = this.currentTutorialLectureId();
        });
    }

    toggleSidebar() {
        this.isCollapsed.update((collapsed) => !collapsed);
        this.courseOverviewService.setSidebarCollapseState('tutorialGroup', this.isCollapsed());
    }

    onSubRouteActivate(componentRef: unknown) {
        if (componentRef instanceof CourseTutorialGroupDetailContainerComponent || componentRef instanceof CourseLectureDetailsComponent) {
            this.activeDetail.set(componentRef);
        }
    }

    setPageTitle(pageTitle: string): void {
        this.pageTitle.set(pageTitle);
    }

    private setTutorialGroupsAndTutorialLectures(courseId: number) {
        const course = this.courseStorageService.getCourse(courseId);
        const cachedTutorialGroups = course?.tutorialGroups;
        const cachedLectures = course?.lectures;
        if (cachedTutorialGroups) {
            this.tutorialGroups.set(cachedTutorialGroups);
        } else {
            this.loadAndSetTutorialGroups(courseId);
        }
        if (cachedLectures) {
            this.tutorialLectures.set(cachedLectures.filter((lecture) => lecture.isTutorialLecture));
        } else {
            this.loadAndSetTutorialLectures(courseId);
        }
    }

    private loadAndSetTutorialGroups(courseId: number) {
        this.tutorialGroupApiService
            .getTutorialGroupsForCourse(courseId)
            .pipe(map((tutorialGroups) => convertTutorialGroupSummaryArrayDatesFromServer(tutorialGroups)))
            .subscribe({
                next: (tutorialGroups) => {
                    this.tutorialGroups.set(tutorialGroups);
                    this.updateCachedTutorialGroups(tutorialGroups, courseId);
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
    }

    private updateCachedTutorialGroups(tutorialGroups: TutorialGroup[], courseId: number) {
        const course = this.courseStorageService.getCourse(courseId);
        if (course) {
            course.tutorialGroups = tutorialGroups;
            this.courseStorageService.updateCourse(course);
        }
    }

    private loadAndSetTutorialLectures(courseId: number) {
        this.lectureService.findAllTutorialLecturesByCourseId(courseId).subscribe({
            next: ({ body }) => {
                const tutorialLectures = body ?? [];
                this.tutorialLectures.set(tutorialLectures);
                this.updateCachedLectures(tutorialLectures, courseId);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private updateCachedLectures(lecturesToUpdate: Lecture[], courseId: number) {
        const course = this.courseStorageService.getCourse(courseId);
        if (!course) {
            return;
        }
        const existingLectures = course.lectures ?? [];
        // Replace the tutorial subset wholesale rather than merging by id. Merging cannot express a deletion: a refresh
        // that returns nothing removed nothing, so a deleted tutorial lecture stayed in the stored course and came
        // straight back the next time the tab read its cache. Non-tutorial lectures belong to the lectures tab and are
        // kept, as is anything the fresh response re-supplies under a different flag.
        const freshLectureIds = new Set(lecturesToUpdate.map((lecture) => lecture.id));
        const retainedLectures = existingLectures.filter((existing) => !existing.isTutorialLecture && !freshLectureIds.has(existing.id));
        course.lectures = [...retainedLectures, ...lecturesToUpdate];
        // Enriching the cached course in place must not change its loaded-ness: preserve the fully-loaded marker
        // the CourseOverviewGuard relies on, otherwise switching to a guarded tab would no longer be access-checked.
        this.courseStorageService.updateCourse(course);
    }

    private prepareSidebarData(tutorialGroups: TutorialGroup[], tutorialLectures: Lecture[]) {
        const tutorialGroupCardElements = this.courseOverviewService.mapTutorialGroupsToSidebarCardElements(tutorialGroups);
        const tutorialLectureCardElements = this.courseOverviewService.mapLecturesToSidebarCardElements(tutorialLectures);
        const cardElements = [...tutorialGroupCardElements, ...tutorialLectureCardElements];
        const accordionGroups: AccordionGroups = this.createAccordionGroups(tutorialGroups, tutorialLectures);
        this.sidebarData.set({
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: accordionGroups,
            ungroupedData: cardElements,
        });
    }

    private createAccordionGroups(tutorialGroups: TutorialGroup[], tutorialLectures: Lecture[]): AccordionGroups {
        const accordionGroups: AccordionGroups = {
            allGroups: { entityData: [] },
            registeredGroups: { entityData: [] },
            furtherGroups: { entityData: [] },
            allTutorialLectures: { entityData: [] },
            currentTutorialLecture: { entityData: [] },
            furtherTutorialLectures: { entityData: [] },
        };
        let tutorialGroupCategory: TutorialGroupCategory;

        const hasUserAtLeastOneTutorialGroup = tutorialGroups.some((tutorialGroup) => tutorialGroup.isUserRegistered || tutorialGroup.isUserTutor);
        tutorialGroups.forEach((tutorialGroup) => {
            const tutorialGroupCardItem = this.courseOverviewService.mapTutorialGroupToSidebarCardElement(tutorialGroup);
            if (!hasUserAtLeastOneTutorialGroup) {
                tutorialGroupCategory = 'allGroups';
            } else {
                tutorialGroupCategory = tutorialGroup.isUserTutor || tutorialGroup.isUserRegistered ? 'registeredGroups' : 'furtherGroups';
            }
            accordionGroups[tutorialGroupCategory].entityData.push(tutorialGroupCardItem);
        });

        const now = dayjs();
        const currentLectures = tutorialLectures.filter(
            (lecture) => lecture.startDate && lecture.startDate.isSameOrBefore(now) && (!lecture.endDate || now.isSameOrBefore(lecture.endDate)),
        );
        const mostRecentlyStartedCurrentLecture =
            currentLectures.length === 0 ? undefined : currentLectures.reduce((latest, current) => (current.startDate!.isAfter(latest.startDate) ? current : latest));
        tutorialLectures.forEach((tutorialLecture) => {
            const tutorialLectureCardItem = this.courseOverviewService.mapLectureToSidebarCardElement(tutorialLecture);
            if (!mostRecentlyStartedCurrentLecture) {
                tutorialGroupCategory = 'allTutorialLectures';
            } else {
                const isCurrentTutorialLecture = mostRecentlyStartedCurrentLecture ? tutorialLecture.id === mostRecentlyStartedCurrentLecture.id : false;
                tutorialGroupCategory = isCurrentTutorialLecture ? 'currentTutorialLecture' : 'furtherTutorialLectures';
            }
            accordionGroups[tutorialGroupCategory].entityData.push(tutorialLectureCardItem);
        });
        return accordionGroups;
    }

    private autoNavigateToLastSelectedOrUpcomingTutorialGroup(tutorialGroups: TutorialGroup[]) {
        const upcomingTutorialGroup = this.courseOverviewService.getUpcomingTutorialGroup(tutorialGroups);
        const lastSelectedSubRoute = this.getLastSelectedSubRoute();
        const nothingSelected = !this.itemSelected();
        if (nothingSelected && lastSelectedSubRoute) {
            void this.router.navigate([lastSelectedSubRoute], { relativeTo: this.activatedRoute, replaceUrl: true });
        } else if (nothingSelected && upcomingTutorialGroup) {
            void this.router.navigate([upcomingTutorialGroup.id], { relativeTo: this.activatedRoute, replaceUrl: true });
        }
    }

    private getLastSelectedSubRoute(): string | undefined {
        return this.sessionStorageService.retrieve<string>('sidebar.lastSelectedItem.tutorialGroup.byCourse.' + this.courseId());
    }

    private getCurrentCourseIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.activatedRoute.parent!.paramMap.pipe(
                map((parameterMap): number | undefined => {
                    const courseIdParameter = parameterMap.get('courseId');
                    return courseIdParameter !== null ? Number(courseIdParameter) : undefined;
                }),
                distinctUntilChanged<number | undefined>(),
            ),
            { initialValue: undefined },
        );
    }

    private computeCurrentTutorialLectureId(): number | undefined {
        const sidebarData = this.sidebarData();
        if (!sidebarData) {
            return undefined;
        }
        const groupedData = sidebarData.groupedData;
        if (!groupedData) {
            return undefined;
        }
        const currentTutorialLecture = groupedData.currentTutorialLecture.entityData.at(0);
        return currentTutorialLecture ? (currentTutorialLecture.id as number) : undefined;
    }

    private getItemSelectedSignal(): Signal<boolean> {
        return toSignal(
            this.router.events.pipe(
                filter((event) => event instanceof NavigationEnd),
                map(() => !!this.activatedRoute.firstChild),
            ),
            { initialValue: !!this.activatedRoute.firstChild },
        );
    }
}
