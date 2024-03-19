import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { LectureGroups, SidebarData, TimeGroupCategory } from 'app/types/sidebar';
import { cloneDeep } from 'lodash-es';

const DEFAULT_EXERCISE_GROUPS: LectureGroups = {
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
    lectureGroups: LectureGroups;
    sortedLectures: Lecture[] | undefined;

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private router: Router,
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

        const upcomingExercise = this.getUpcomingLecture();
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

    getUpcomingLecture(): Lecture | undefined {
        if (this.course) {
            const upcomingLecture = this.course.lectures?.reduce((a, b) => {
                return (a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b;
            });
            return upcomingLecture;
        }
    }

    prepareSidebarData() {
        this.sortedLectures = this.sortLectures();
        this.lectureGroups = this.groupLecturesByStartDate();
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

    private groupLecturesByStartDate(): LectureGroups {
        const groupedExerciseGroups: LectureGroups = cloneDeep(DEFAULT_EXERCISE_GROUPS);
        if (!this.sortedLectures) {
            return groupedExerciseGroups;
        }

        for (const exercise of this.sortedLectures) {
            const exerciseGroup = this.getCorrespondingLectureGroup(exercise);
            groupedExerciseGroups[exerciseGroup].entityData.push(exercise);
        }

        return groupedExerciseGroups;
    }

    private getCorrespondingLectureGroup(lecture: Lecture): TimeGroupCategory {
        if (!lecture.startDate) {
            return 'noDueDate';
        }

        const dueDate = dayjs(lecture.startDate);
        const now = dayjs();

        const dueDateIsInThePast = dueDate.isBefore(now);
        if (dueDateIsInThePast) {
            return 'past';
        }

        const dueDateIsWithinNextWeek = dueDate.isBefore(now.add(1, 'week'));
        if (dueDateIsWithinNextWeek) {
            return 'current';
        }

        return 'future';
    }

    /**
     * Reorders all displayed lectures
     */

    // public groupLectures(): void {
    //     this.weeklyLecturesGrouped = {};
    //     this.weeklyIndexKeys = [];
    //     const groupedLectures = {};
    //     const indexKeys: string[] = [];
    //     const courseLectures = this.course?.lectures ? [...this.course!.lectures] : [];
    //     const sortedLectures = this.sortLectures(courseLectures, this.sortingOrder);
    //     const notAssociatedLectures: Lecture[] = [];
    //     sortedLectures.forEach((lecture) => {
    //         const dateValue = lecture.startDate ? dayjs(lecture.startDate) : undefined;
    //         if (!dateValue) {
    //             notAssociatedLectures.push(lecture);
    //             return;
    //         }
    //         const dateIndex = dateValue ? dayjs(dateValue).startOf('week').format('YYYY-MM-DD') : 'NoDate';
    //         if (!groupedLectures[dateIndex]) {
    //             indexKeys.push(dateIndex);
    //             if (dateValue) {
    //                 groupedLectures[dateIndex] = {
    //                     start: dayjs(dateValue).startOf('week'),
    //                     end: dayjs(dateValue).endOf('week'),
    //                     isCollapsed: dateValue.isBefore(dayjs(), 'week') || dateValue.isAfter(dayjs(), 'week'),
    //                     isCurrentWeek: dateValue.isSame(dayjs(), 'week'),
    //                     lectures: [],
    //                 };
    //             } else {
    //                 groupedLectures[dateIndex] = {
    //                     isCollapsed: false,
    //                     isCurrentWeek: false,
    //                     lectures: [],
    //                 };
    //             }
    //         }
    //         groupedLectures[dateIndex].lectures.push(lecture);
    //     });
    //     if (notAssociatedLectures.length > 0) {
    //         this.weeklyLecturesGrouped = {
    //             ...groupedLectures,
    //             noDate: {
    //                 label: this.translateService.instant('artemisApp.courseOverview.exerciseList.noExerciseDate'),
    //                 isCollapsed: false,
    //                 isCurrentWeek: false,
    //                 lectures: notAssociatedLectures,
    //             },
    //         };
    //         this.weeklyIndexKeys = [...indexKeys, 'noDate'];
    //     } else {
    //         this.weeklyLecturesGrouped = groupedLectures;
    //         this.weeklyIndexKeys = indexKeys;
    //     }
    // }

    private sortLectures(): Lecture[] | undefined {
        const sortedEntityData = this.course?.lectures?.sort((a, b) => {
            const startDateA = a.startDate ? a.startDate.valueOf() : dayjs().valueOf();
            const startDateB = b.startDate ? b.startDate.valueOf() : dayjs().valueOf();

            if (startDateB - startDateA !== 0) {
                return startDateB - startDateA;
            }
            // If Start Date is identical or undefined sort by title
            return a.title && b.title ? a.title.localeCompare(b.title) : 0;
        });

        return sortedEntityData;
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
    }
}
