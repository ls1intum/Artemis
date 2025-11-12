import { Component, Signal, computed, effect, inject, signal } from '@angular/core';
import { distinctUntilChanged } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { filter, map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { NgClass } from '@angular/common';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AccordionGroups, CollapseState, SidebarData, SidebarItemShowAlways, TutorialGroupCategory } from 'app/shared/types/sidebar';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseTutorialGroupsComponent {
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
    private tutorialGroupService = inject(TutorialGroupsService);
    private lectureService = inject(LectureService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);

    courseId = this.getCurrentCourseIdSignal();
    tutorialGroups = signal<TutorialGroup[]>([]);
    tutorialLectures = signal<Lecture[]>([]);
    sidebarData = signal<SidebarData | undefined>(undefined);
    itemSelected = this.getItemSelectedSignal();
    isCollapsed = false;
    currentTutorialLectureId = computed(() => this.computeCurrentTutorialLectureId());

    constructor() {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('tutorialGroup');

        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.setTutorialGroupsAndTutorialLectures(courseId);
            }
        });

        effect(() => {
            const tutorialGroups = this.tutorialGroups();
            const tutorialLectures = this.tutorialLectures();
            if (tutorialGroups.length > 0 || tutorialLectures.length > 0) {
                this.prepareSidebarData(tutorialGroups, tutorialLectures);
                this.autoNavigateToLastSelectedOrUpcomingTutorialGroup(tutorialGroups);
            }
        });

        effect(() => {
            this.lectureService.currentTutorialLectureId = this.currentTutorialLectureId();
        });
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('tutorialGroup', this.isCollapsed);
    }

    private setTutorialGroupsAndTutorialLectures(courseId: number) {
        const course = this.courseStorageService.getCourse(courseId);
        const cachedTutorialGroups = course?.tutorialGroups;
        const cachedLectures = course?.lectures;
        if (cachedTutorialGroups !== undefined) {
            this.tutorialGroups.set(cachedTutorialGroups);
        } else {
            this.loadAndSetTutorialGroups(courseId);
        }
        if (cachedLectures !== undefined) {
            this.tutorialLectures.set(cachedLectures.filter((lecture) => lecture.isTutorialLecture));
        } else {
            this.loadAndSetTutorialLectures(courseId);
        }
    }

    private loadAndSetTutorialGroups(courseId: number) {
        this.tutorialGroupService.getAllForCourse(courseId).subscribe({
            next: ({ body }) => {
                const tutorialGroups = body ?? [];
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
        const remainingLectures = existingLectures.filter((existing) => !lecturesToUpdate.some((updated) => updated.id === existing.id));
        course.lectures = [...remainingLectures, ...lecturesToUpdate];
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
            this.router.navigate([lastSelectedSubRoute], { relativeTo: this.activatedRoute, replaceUrl: true });
        } else if (nothingSelected && upcomingTutorialGroup) {
            this.router.navigate([upcomingTutorialGroup.id], { relativeTo: this.activatedRoute, replaceUrl: true });
        }
    }

    private getLastSelectedSubRoute(): string | undefined {
        return this.sessionStorageService.retrieve<string>('sidebar.lastSelectedItem.tutorialGroup.byCourse.' + this.courseId());
    }

    private getCurrentCourseIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.activatedRoute.parent!.paramMap.pipe(
                map((parameterMap) => {
                    const courseIdParameter = parameterMap.get('courseId');
                    return courseIdParameter !== null ? Number(courseIdParameter) : undefined;
                }),
                distinctUntilChanged(),
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
