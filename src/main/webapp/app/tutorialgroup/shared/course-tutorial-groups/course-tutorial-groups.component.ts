import { Component, Signal, effect, inject, signal } from '@angular/core';
import { distinctUntilChanged } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { cloneDeep } from 'lodash-es';
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
    private readonly EMPTY_TUTORIAL_UNIT_GROUPS: AccordionGroups = {
        registeredGroups: { entityData: [] },
        furtherGroups: { entityData: [] },
        allGroups: { entityData: [] },
        currentTutorialLecture: { entityData: [] },
        furtherTutorialLectures: { entityData: [] },
    };
    protected readonly DEFAULT_COLLAPSE_STATE: CollapseState = {
        registeredGroups: false,
        allGroups: true,
        furtherGroups: true,
        currentTutorialLecture: false,
        furtherTutorialLectures: true,
    };
    protected readonly DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
        registeredGroups: false,
        allGroups: false,
        furtherGroups: false,
        currentTutorialLecture: true,
        furtherTutorialLectures: false,
    };

    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private courseStorageService = inject(CourseStorageService);
    private tutorialGroupService = inject(TutorialGroupsService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private lectureService = inject(LectureService);

    courseId = this.getCurrentCourseIdSignal();
    tutorialGroups = signal<TutorialGroup[]>([]);
    tutorialLectures = signal<Lecture[]>([]);
    sidebarData = signal<SidebarData | undefined>(undefined);
    itemSelected = signal(true);
    isCollapsed = false;

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
                this.navigateToTutorialGroup(tutorialGroups);
            }
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
            this.tutorialLectures.set(cachedLectures.filter((lecture) => lecture.tutorialLecture !== true));
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
        this.lectureService.findAllByCourseId(courseId).subscribe({
            next: ({ body }) => {
                const lectures = body ?? [];
                const tutorialLectures = lectures.filter((lecture) => lecture.tutorialLecture);
                this.tutorialLectures.set(tutorialLectures);
                this.updateCachedLectures(tutorialLectures, courseId);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private updateCachedLectures(lectures: Lecture[], courseId: number) {
        const course = this.courseStorageService.getCourse(courseId);
        if (course) {
            course.lectures = lectures;
            this.courseStorageService.updateCourse(course);
        }
    }

    private prepareSidebarData(tutorialGroups: TutorialGroup[], tutorialLectures: Lecture[]) {
        const tutorialGroupCardElements = this.courseOverviewService.mapTutorialGroupsToSidebarCardElements(tutorialGroups);
        const tutorialLectureCardElements = this.courseOverviewService.mapTutorialLecturesToSidebarCardElements(tutorialLectures);
        const cardElements = [...tutorialGroupCardElements, ...tutorialLectureCardElements];
        const accordionGroups = this.createAccordionGroups(tutorialGroups, tutorialLectures);
        this.sidebarData.set({
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: accordionGroups,
            ungroupedData: cardElements,
        });
    }

    private createAccordionGroups(tutorialGroups: TutorialGroup[], tutorialLectures: Lecture[]): AccordionGroups {
        const accordionGroups = cloneDeep(this.EMPTY_TUTORIAL_UNIT_GROUPS) as AccordionGroups;
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
        const nonEndedLectures = tutorialLectures.filter((lecture) => lecture.startDate && lecture.endDate && lecture.endDate.isAfter(now));
        const earliestNonEndedLecture =
            nonEndedLectures.length === 0 ? undefined : nonEndedLectures.reduce((earliest, current) => (current.startDate!.isBefore(earliest.startDate!) ? current : earliest));
        tutorialLectures.forEach((tutorialLecture) => {
            const tutorialLectureCardItem = this.courseOverviewService.mapTutorialLectureToSidebarCardElement(tutorialLecture);
            const isCurrentTutorialLecture = earliestNonEndedLecture ? tutorialLecture.id === earliestNonEndedLecture.id : false;
            tutorialGroupCategory = isCurrentTutorialLecture ? 'currentTutorialLecture' : 'furtherTutorialLectures';
            accordionGroups[tutorialGroupCategory].entityData.push(tutorialLectureCardItem);
        });
        return accordionGroups;
    }

    private navigateToTutorialGroup(tutorialGroups: TutorialGroup[]) {
        const upcomingTutorialGroup = this.courseOverviewService.getUpcomingTutorialGroup(tutorialGroups);
        const lastSelectedTutorialGroup = this.getLastSelectedTutorialGroup();
        const tutorialGroupId = this.activatedRoute.firstChild?.snapshot.params.tutorialGroupId;
        const lectureId = this.activatedRoute.firstChild?.snapshot.params.lectureId;
        if (!tutorialGroupId && lastSelectedTutorialGroup) {
            this.router.navigate([lastSelectedTutorialGroup], { relativeTo: this.activatedRoute, replaceUrl: true });
            this.itemSelected.set(true);
        } else if (!tutorialGroupId && upcomingTutorialGroup) {
            this.router.navigate([upcomingTutorialGroup.id], { relativeTo: this.activatedRoute, replaceUrl: true });
            this.itemSelected.set(true);
        } else if (tutorialGroupId || lectureId) {
            this.itemSelected.set(true);
        } else {
            this.itemSelected.set(false);
        }
    }

    private getLastSelectedTutorialGroup(): string | undefined {
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
}
