import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { LectureGroups, SidebarData } from 'app/types/sidebar';
import { CourseOverviewService } from '../course-overview.service';

const DEFAULT_UNIT_GROUPS: LectureGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDueDate: { entityData: [] },
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
    lectureGroups: LectureGroups = DEFAULT_UNIT_GROUPS;
    sortedLectures: Lecture[] = [];

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
        this.lectureGroups = this.courseOverviewService.groupLecturesByStartDate(this.sortedLectures);
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'lecture',
            storageId: 'lecture',
            groupedData: this.lectureGroups,
            ungroupedData: this.sortedLectures,
        };
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
    }
}
