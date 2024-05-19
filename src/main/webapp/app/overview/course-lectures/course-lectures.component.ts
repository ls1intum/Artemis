import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { AccordionGroups, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { CourseOverviewService } from '../course-overview.service';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

@Component({
    selector: 'jhi-course-lectures',
    templateUrl: './course-lectures.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLecturesComponent implements OnInit, OnDestroy {
    private paramSubscription: Subscription;
    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    course?: Course;
    courseId: number;

    lectureSelected: boolean = true;
    sidebarData: SidebarData;
    accordionLectureGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sortedLectures: Lecture[] = [];
    sidebarLectures: SidebarCardElement[] = [];
    isCollapsed: boolean = false;

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private router: Router,
        private courseOverviewService: CourseOverviewService,
    ) {}

    ngOnInit() {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('lecture');
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params.courseId, 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.prepareSidebarData();
        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.prepareSidebarData();
        });

        const upcomingLecture = this.courseOverviewService.getUpcomingLecture(this.course?.lectures);
        const lastSelectedLecture = this.getLastSelectedLecture();
        this.paramSubscription = this.route.params.subscribe((params) => {
            const lectureId = parseInt(params.lectureId, 10);
            // If no lecture is selected navigate to the upcoming lecture
            if (!lectureId && lastSelectedLecture) {
                this.router.navigate([lastSelectedLecture], { relativeTo: this.route, replaceUrl: true });
            } else if (!lectureId && upcomingLecture) {
                this.router.navigate([upcomingLecture.id], { relativeTo: this.route, replaceUrl: true });
            } else {
                this.lectureSelected = lectureId ? true : false;
            }
        });
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

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }
}
