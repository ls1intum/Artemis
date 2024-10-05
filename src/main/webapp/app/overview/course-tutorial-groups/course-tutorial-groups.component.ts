import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, finalize } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { map, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, TutorialGroupCategory } from 'app/types/sidebar';
import { CourseOverviewService } from '../course-overview.service';
import { cloneDeep } from 'lodash-es';

const TUTORIAL_UNIT_GROUPS: AccordionGroups = {
    registered: { entityData: [] },
    further: { entityData: [] },
    all: { entityData: [] },
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    registered: false,
    all: true,
    further: true,
};

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseTutorialGroupsComponent implements OnInit, OnDestroy {
    private router = inject(Router);
    private courseStorageService = inject(CourseStorageService);
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupService = inject(TutorialGroupsService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private cdr = inject(ChangeDetectorRef);
    private courseOverviewService = inject(CourseOverviewService);

    ngUnsubscribe = new Subject<void>();

    tutorialGroups: TutorialGroup[] = [];
    courseId: number;
    course?: Course;
    configuration?: TutorialGroupsConfiguration;
    isLoading = false;
    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];
    isCollapsed: boolean = false;

    tutorialGroupSelected: boolean = true;
    sidebarData: SidebarData;
    sortedTutorialGroups: TutorialGroup[] = [];
    accordionTutorialGroupsGroups: AccordionGroups = TUTORIAL_UNIT_GROUPS;
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    sidebarTutorialGroups: SidebarCardElement[] = [];

    get registeredTutorialGroups() {
        if (this.course?.isAtLeastTutor) {
            return this.tutorialGroups.filter((tutorialGroup) => tutorialGroup.isUserTutor);
        } else {
            return this.tutorialGroups.filter((tutorialGroup) => tutorialGroup.isUserRegistered);
        }
    }

    ngOnInit(): void {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('tutorialGroup');

        this.route.parent?.paramMap
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((parentParams) => {
                this.courseId = Number(parentParams.get('courseId'));
                if (this.courseId) {
                    this.setCourse();
                    this.setTutorialGroups();
                    this.prepareSidebarData();
                    this.subscribeToCourseUpdates();
                }
            })
            .add(() => this.cdr.detectChanges());
    }

    navigateToTutorialGroup() {
        const upcomingTutorialGroup = this.courseOverviewService.getUpcomingTutorialGroup(this.tutorialGroups);
        const lastSelectedTutorialGroup = this.getLastSelectedTutorialGroup();
        const tutorialGroupId = this.route.firstChild?.snapshot.params.tutorialGroupId;
        if (!tutorialGroupId && lastSelectedTutorialGroup) {
            this.router.navigate([lastSelectedTutorialGroup], { relativeTo: this.route, replaceUrl: true });
            this.tutorialGroupSelected = true;
        } else if (!tutorialGroupId && upcomingTutorialGroup) {
            this.router.navigate([upcomingTutorialGroup.id], { relativeTo: this.route, replaceUrl: true });
            this.tutorialGroupSelected = true;
        } else {
            this.tutorialGroupSelected = tutorialGroupId ? true : false;
        }
    }

    subscribeToCourseUpdates() {
        this.courseStorageService
            .subscribeToCourseUpdates(this.courseId)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((course) => {
                this.course = course;
                this.configuration = course?.tutorialGroupsConfiguration;
                this.setFreeDays();
                this.setTutorialGroups();
                this.prepareSidebarData();
            })
            .add(() => this.cdr.detectChanges());
    }

    prepareSidebarData() {
        if (!this.course?.tutorialGroups) {
            return;
        }
        this.sidebarTutorialGroups = this.courseOverviewService.mapTutorialGroupsToSidebarCardElements(this.tutorialGroups);
        this.accordionTutorialGroupsGroups = this.groupTutorialGroupsByRegistration();
        this.updateSidebarData();
    }

    groupTutorialGroupsByRegistration(): AccordionGroups {
        const groupedTutorialGroupGroups = cloneDeep(TUTORIAL_UNIT_GROUPS) as AccordionGroups;
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

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            storageId: 'tutorialGroup',
            groupedData: this.accordionTutorialGroupsGroups,
            ungroupedData: this.sidebarTutorialGroups,
        };
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('tutorialGroup', this.isCollapsed);
        this.cdr.detectChanges();
    }

    getLastSelectedTutorialGroup(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.tutorialGroup.byCourse.' + this.courseId);
    }

    private setFreeDays() {
        if (this.course?.tutorialGroupsConfiguration?.tutorialGroupFreePeriods) {
            this.tutorialGroupFreeDays = this.course.tutorialGroupsConfiguration.tutorialGroupFreePeriods;
        } else {
            this.tutorialGroupFreeDays = [];
        }
    }

    setTutorialGroups() {
        const tutorialGroupsLoadedFromCache = this.loadTutorialGroupsFromCache();
        if (!tutorialGroupsLoadedFromCache) {
            this.loadTutorialGroupsFromServer();
        }
        this.navigateToTutorialGroup();
    }

    setCourse() {
        const courseLoadedFromCache = this.loadCourseFromCache();
        if (!courseLoadedFromCache) {
            this.loadCourseFromServer();
        }
    }

    loadCourseFromCache() {
        const cachedCourse = this.courseStorageService.getCourse(this.courseId);
        if (cachedCourse === undefined) {
            return false;
        } else {
            this.course = cachedCourse;
            this.configuration = this.course?.tutorialGroupsConfiguration;
            this.setFreeDays();
            return true;
        }
    }

    loadTutorialGroupsFromCache(): boolean {
        const cachedTutorialGroups = this.courseStorageService.getCourse(this.courseId)?.tutorialGroups;
        if (cachedTutorialGroups === undefined) {
            return false;
        } else {
            this.tutorialGroups = cachedTutorialGroups;
            return true;
        }
    }

    updateCachedTutorialGroups() {
        const course = this.courseStorageService.getCourse(this.courseId);
        if (course) {
            course.tutorialGroups = this.tutorialGroups;
            this.courseStorageService.updateCourse(course);
        }
    }

    loadTutorialGroupsFromServer() {
        this.isLoading = true;
        this.tutorialGroupService
            .getAllForCourse(this.courseId)
            .pipe(
                map((res: HttpResponse<TutorialGroup[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
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

    loadCourseFromServer() {
        this.isLoading = true;
        this.courseManagementService
            .find(this.courseId)
            .pipe(
                map((res: HttpResponse<Course>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (course: Course) => {
                    this.course = course;
                    this.configuration = this.course?.tutorialGroupsConfiguration;
                    this.setFreeDays();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            })
            .add(() => this.cdr.detectChanges());
    }

    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToTutorialGroup();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
