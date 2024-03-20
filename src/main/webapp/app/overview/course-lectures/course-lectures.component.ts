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
    private courseId: number;
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    course?: Course;

    lectureSelected: boolean = true;
    sidebarData: SidebarData;
    accordionLectureGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sortedLectures: Lecture[] = [];
    sidebarLectures: SidebarCardElement[] = [];

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private router: Router,
        private courseOverviewService: CourseOverviewService,
    ) {}

    ngOnInit() {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params.courseId, 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.prepareSidebarData();
        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.prepareSidebarData();
        });

        const upcomingExercise = this.courseOverviewService.getUpcomingLecture(this.course?.lectures);
        this.paramSubscription = this.route.params.subscribe((params) => {
            const lectureId = parseInt(params.lectureId, 10);
            // If no exercise is selected navigate to the upcoming exercise
            if (!lectureId && upcomingExercise) {
                this.router.navigate([upcomingExercise.id], { relativeTo: this.route });
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

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
    }
}
