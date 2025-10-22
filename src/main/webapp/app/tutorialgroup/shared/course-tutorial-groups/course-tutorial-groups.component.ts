import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, Signal, effect, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { distinctUntilChanged } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { map, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
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

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseTutorialGroupsComponent implements OnInit, OnDestroy {
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
    private cdr = inject(ChangeDetectorRef);
    private courseStorageService = inject(CourseStorageService);
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupService = inject(TutorialGroupsService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private ngUnsubscribe = new Subject<void>();
    private course?: Course;
    private tutorialGroups: TutorialGroup[] = [];
    private accordionGroups: AccordionGroups = this.EMPTY_TUTORIAL_UNIT_GROUPS;
    private sidebarCardElements: SidebarCardElement[] = [];

    tutorialGroupSelected = signal(true);
    courseId = this.getCurrentCourseIdSignal();
    sidebarData: SidebarData;
    isCollapsed = false;

    constructor() {
        effect(() => {
            if (this.courseId()) {
                this.setCourse();
                this.setTutorialGroups();
                this.prepareSidebarData();
                this.subscribeToCourseUpdates();
                this.cdr.detectChanges();
            }
        });
    }

    ngOnInit(): void {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('tutorialGroup');
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onSubRouteDeactivate() {
        if (this.activatedRoute.firstChild) {
            return;
        }
        this.navigateToTutorialGroup();
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('tutorialGroup', this.isCollapsed);
        this.cdr.detectChanges();
    }

    private setCourse() {
        const courseLoadedFromCache = this.loadCourseFromCache();
        if (!courseLoadedFromCache) {
            this.loadCourseFromServer();
        }
    }

    private loadCourseFromCache(): boolean {
        const courseId = this.courseId();
        if (courseId) {
            const cachedCourse = this.courseStorageService.getCourse(courseId);
            if (cachedCourse !== undefined) {
                this.course = cachedCourse;
                return true;
            }
        }
        return false;
    }

    private loadCourseFromServer() {
        const courseId = this.courseId();
        if (!courseId) {
            return;
        }
        this.courseManagementService
            .find(courseId)
            .pipe(
                map((res: HttpResponse<Course>) => res.body),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (course: Course) => {
                    this.course = course;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            })
            .add(() => this.cdr.detectChanges());
    }

    private setTutorialGroups() {
        const tutorialGroupsLoadedFromCache = this.loadTutorialGroupsFromCache();
        if (!tutorialGroupsLoadedFromCache) {
            this.loadTutorialGroupsFromServer();
        }
        this.navigateToTutorialGroup();
    }

    private loadTutorialGroupsFromCache(): boolean {
        const courseId = this.courseId();
        if (courseId) {
            const cachedTutorialGroups = this.courseStorageService.getCourse(courseId)?.tutorialGroups;
            if (cachedTutorialGroups !== undefined) {
                this.tutorialGroups = cachedTutorialGroups;
                return true;
            }
        }
        return false;
    }

    private loadTutorialGroupsFromServer() {
        const courseId = this.courseId();
        if (!courseId) {
            return;
        }
        this.tutorialGroupService
            .getAllForCourse(courseId)
            .pipe(
                map((res: HttpResponse<TutorialGroup[]>) => res.body),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroups: TutorialGroup[]) => {
                    this.tutorialGroups = tutorialGroups ?? [];
                    this.updateCachedTutorialGroups();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            })
            .add(() => this.cdr.detectChanges());
    }

    private updateCachedTutorialGroups() {
        const courseId = this.courseId();
        if (courseId) {
            const course = this.courseStorageService.getCourse(courseId);
            if (course) {
                course.tutorialGroups = this.tutorialGroups;
                this.courseStorageService.updateCourse(course);
            }
        }
    }

    private prepareSidebarData() {
        if (!this.course?.tutorialGroups) {
            return;
        }
        this.sidebarCardElements = this.courseOverviewService.mapTutorialGroupsToSidebarCardElements(this.tutorialGroups);
        this.accordionGroups = this.groupTutorialGroupsByRegistration();
        this.updateSidebarData();
    }

    private groupTutorialGroupsByRegistration(): AccordionGroups {
        const groupedTutorialGroupGroups = cloneDeep(this.EMPTY_TUTORIAL_UNIT_GROUPS) as AccordionGroups;
        let tutorialGroupCategory: TutorialGroupCategory;

        const hasUserAtLeastOneTutorialGroup = this.tutorialGroups.some((tutorialGroup) => tutorialGroup.isUserRegistered || tutorialGroup.isUserTutor);
        this.tutorialGroups.forEach((tutorialGroup) => {
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

    private updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: this.accordionGroups,
            ungroupedData: this.sidebarCardElements,
        };
    }

    private subscribeToCourseUpdates() {
        const courseId = this.courseId();
        if (!courseId) {
            return;
        }
        this.courseStorageService
            .subscribeToCourseUpdates(courseId)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((course) => {
                this.course = course;
                this.setTutorialGroups();
                this.prepareSidebarData();
            })
            .add(() => this.cdr.detectChanges());
    }

    private navigateToTutorialGroup() {
        const upcomingTutorialGroup = this.courseOverviewService.getUpcomingTutorialGroup(this.tutorialGroups);
        const lastSelectedTutorialGroup = this.getLastSelectedTutorialGroup();
        const tutorialGroupId = this.activatedRoute.firstChild?.snapshot.params.tutorialGroupId;
        if (!tutorialGroupId && lastSelectedTutorialGroup) {
            this.router.navigate([lastSelectedTutorialGroup], { relativeTo: this.activatedRoute, replaceUrl: true });
            this.tutorialGroupSelected.set(true);
        } else if (!tutorialGroupId && upcomingTutorialGroup) {
            this.router.navigate([upcomingTutorialGroup.id], { relativeTo: this.activatedRoute, replaceUrl: true });
            this.tutorialGroupSelected.set(true);
        } else {
            this.tutorialGroupSelected.set(tutorialGroupId ? true : false);
        }
    }

    private getLastSelectedTutorialGroup(): string | undefined {
        return this.sessionStorageService.retrieve<string>('sidebar.lastSelectedItem.tutorialGroup.byCourse.' + this.courseId);
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
