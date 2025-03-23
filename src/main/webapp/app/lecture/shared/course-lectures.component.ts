import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { CourseStorageService } from 'app/core/course/manage/course-storage.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/types/sidebar';
import { NgClass } from '@angular/common';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/core/course/overview/course-overview.service';

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
    styleUrls: ['../../overview/course-overview.scss'],
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseLecturesComponent implements OnInit, OnDestroy {
    private courseStorageService = inject(CourseStorageService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private courseOverviewService = inject(CourseOverviewService);

    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    course?: Course;
    courseId: number;

    lectureSelected = true;
    sidebarData: SidebarData;
    accordionLectureGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sortedLectures: Lecture[] = [];
    sidebarLectures: SidebarCardElement[] = [];
    isCollapsed = false;
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    ngOnInit() {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('lecture');
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.prepareSidebarData();
        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.prepareSidebarData();
        });

        // If no lecture is selected navigate to the lastSelected or upcoming lecture
        this.navigateToLecture();
    }

    navigateToLecture() {
        const upcomingLecture = this.courseOverviewService.getUpcomingLecture(this.course?.lectures);
        const lastSelectedLecture = this.getLastSelectedLecture();
        const lectureId = this.route.firstChild?.snapshot.params.lectureId;
        if (!lectureId && lastSelectedLecture) {
            this.router.navigate([lastSelectedLecture], { relativeTo: this.route, replaceUrl: true });
        } else if (!lectureId && upcomingLecture) {
            this.router.navigate([upcomingLecture.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            this.lectureSelected = !!lectureId;
        }
    }

    prepareSidebarData() {
        if (!this.course?.lectures) {
            return;
        }
        this.sortedLectures = this.courseOverviewService.sortLectures(this.course.lectures);
        this.sidebarLectures = this.courseOverviewService.mapLecturesToSidebarCardElements(this.sortedLectures);
        this.accordionLectureGroups = this.courseOverviewService.groupLecturesByStartDate(this.sortedLectures);
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            storageId: 'lecture',
            groupedData: this.accordionLectureGroups,
            ungroupedData: this.sidebarLectures,
        };
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('lecture', this.isCollapsed);
    }

    getLastSelectedLecture(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.lecture.byCourse.' + this.courseId);
    }

    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToLecture();
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }
}
