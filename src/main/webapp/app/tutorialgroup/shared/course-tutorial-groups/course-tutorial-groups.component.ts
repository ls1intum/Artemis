import { Component, Signal, effect, inject, signal } from '@angular/core';
import { distinctUntilChanged, startWith, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
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
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways, TutorialGroupCategory } from 'app/shared/types/sidebar';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Course } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseTutorialGroupsComponent {
    private readonly EMPTY_TUTORIAL_UNIT_GROUPS = {
        registered: { entityData: [] },
        further: { entityData: [] },
        all: { entityData: [] },
    };
    protected readonly DEFAULT_COLLAPSE_STATE: CollapseState = {
        registered: false,
        all: true,
        further: true,
    };
    protected readonly DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
        registered: false,
        all: false,
        further: false,
    };

    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private courseStorageService = inject(CourseStorageService);
    private tutorialGroupService = inject(TutorialGroupsService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private accordionGroups: AccordionGroups = this.EMPTY_TUTORIAL_UNIT_GROUPS;
    private sidebarCardElements: SidebarCardElement[] = [];
    private updatedCourse: Signal<Course | undefined>;

    tutorialGroups = signal<TutorialGroup[]>([]);
    tutorialGroupSelected = signal(true);
    courseId = this.getCurrentCourseIdSignal();
    sidebarData = signal<SidebarData | undefined>(undefined);
    isCollapsed = false;

    constructor() {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('tutorialGroup');
        this.updatedCourse = this.getUpdatedCourseSignal();

        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.setTutorialGroups(courseId);
            }
        });

        effect(() => {
            const tutorialGroups = this.tutorialGroups();
            if (tutorialGroups.length > 0) {
                this.prepareSidebarData(tutorialGroups);
                this.navigateToTutorialGroup(tutorialGroups);
            }
        });

        effect(() => {
            const updatedCourse = this.updatedCourse();
            if (updatedCourse !== undefined) {
                this.tutorialGroups.set(updatedCourse.tutorialGroups ?? []);
            }
        });
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('tutorialGroup', this.isCollapsed);
    }

    private updateCachedTutorialGroups(tutorialGroups: TutorialGroup[]) {
        const courseId = this.courseId();
        if (courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            if (course) {
                course.tutorialGroups = tutorialGroups;
                this.courseStorageService.updateCourse(course);
            }
        }
    }

    private prepareSidebarData(tutorialGroups: TutorialGroup[]) {
        this.sidebarCardElements = this.courseOverviewService.mapTutorialGroupsToSidebarCardElements(tutorialGroups);
        this.accordionGroups = this.groupTutorialGroupsByRegistration(tutorialGroups);
        this.sidebarData.set({
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: this.accordionGroups,
            ungroupedData: this.sidebarCardElements,
        });
    }

    private groupTutorialGroupsByRegistration(tutorialGroups: TutorialGroup[]): AccordionGroups {
        const groupedTutorialGroupGroups = cloneDeep(this.EMPTY_TUTORIAL_UNIT_GROUPS) as AccordionGroups;
        let tutorialGroupCategory: TutorialGroupCategory;

        const hasUserAtLeastOneTutorialGroup = tutorialGroups.some((tutorialGroup) => tutorialGroup.isUserRegistered || tutorialGroup.isUserTutor);
        tutorialGroups.forEach((tutorialGroup) => {
            const tutorialGroupCardItem = this.courseOverviewService.mapTutorialGroupToSidebarCardElement(tutorialGroup);
            if (!hasUserAtLeastOneTutorialGroup) {
                tutorialGroupCategory = 'all';
            } else {
                tutorialGroupCategory = tutorialGroup.isUserTutor || tutorialGroup.isUserRegistered ? 'registered' : 'further';
            }
            groupedTutorialGroupGroups[tutorialGroupCategory].entityData.push(tutorialGroupCardItem);
        });
        return groupedTutorialGroupGroups;
    }

    private setTutorialGroups(courseId: number) {
        const cachedTutorialGroups = this.courseStorageService.getCourse(courseId)?.tutorialGroups;
        if (cachedTutorialGroups !== undefined) {
            this.tutorialGroups.set(cachedTutorialGroups);
        } else {
            this.loadTutorialGroupsFromServer(courseId);
        }
    }

    private loadTutorialGroupsFromServer(courseId: number): void {
        this.tutorialGroupService.getAllForCourse(courseId).subscribe({
            next: ({ body }) => {
                const tutorialGroups = body ?? [];
                this.tutorialGroups.set(tutorialGroups);
                this.updateCachedTutorialGroups(tutorialGroups);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private navigateToTutorialGroup(tutorialGroups: TutorialGroup[]) {
        const upcomingTutorialGroup = this.courseOverviewService.getUpcomingTutorialGroup(tutorialGroups);
        const lastSelectedTutorialGroup = this.getLastSelectedTutorialGroup();
        const tutorialGroupId = this.activatedRoute.firstChild?.snapshot.params.tutorialGroupId;
        if (!tutorialGroupId && lastSelectedTutorialGroup) {
            this.router.navigate([lastSelectedTutorialGroup], { relativeTo: this.activatedRoute, replaceUrl: true });
            this.tutorialGroupSelected.set(true);
        } else if (!tutorialGroupId && upcomingTutorialGroup) {
            this.router.navigate([upcomingTutorialGroup.id], { relativeTo: this.activatedRoute, replaceUrl: true });
            this.tutorialGroupSelected.set(true);
        } else {
            this.tutorialGroupSelected.set(!!tutorialGroupId);
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

    private getUpdatedCourseSignal(): Signal<Course | undefined> {
        const updatedCourseObservable = toObservable(this.courseId).pipe(
            distinctUntilChanged(),
            switchMap((courseId) => (courseId == null ? of(undefined) : this.courseStorageService.subscribeToCourseUpdates(courseId))),
            startWith(undefined),
        );
        return toSignal(updatedCourseObservable, { initialValue: undefined });
    }
}
