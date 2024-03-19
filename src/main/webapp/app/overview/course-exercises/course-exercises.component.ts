import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise } from 'app/entities/exercise.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs';
import { ExerciseGroups, SidebarData, TimeGroupCategory } from 'app/types/sidebar';

//Todo einheitlöich machen
const DEFAULT_EXERCISE_GROUPS: ExerciseGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDueDate: { entityData: [] },
};

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesComponent implements OnInit, OnDestroy {
    private courseId: number;
    private paramSubscription: Subscription;
    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;

    course?: Course;
    sortedExercises?: Exercise[] | undefined;
    exerciseForGuidedTour?: Exercise;

    exerciseSelected: boolean = true;
    exerciseGroups: ExerciseGroups = DEFAULT_EXERCISE_GROUPS;
    sidebarData: SidebarData;

    constructor(
        private courseStorageService: CourseStorageService,
        private route: ActivatedRoute,
        private guidedTourService: GuidedTourService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params.courseId, 10);
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.onCourseLoad();
        this.prepareSidebarData();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.prepareSidebarData();
            this.onCourseLoad();
        });

        this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, courseExerciseOverviewTour, true);
        const upcomingExercise = this.getUpcomingExercise();
        this.paramSubscription = this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params.exerciseId, 10);
            // If no exercise is selected navigate to the upcoming exercise
            if (!exerciseId && upcomingExercise) {
                this.router.navigate([upcomingExercise.id], { relativeTo: this.route });
            } else {
                this.exerciseSelected = exerciseId ? true : false;
            }
        });
    }

    prepareSidebarData() {
        this.sortedExercises = this.sortEntityData();
        this.exerciseGroups = this.groupExercisesByDueDate();
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exercise',
            storageId: 'exercise',
            groupedData: this.exerciseGroups,
            ungroupedData: this.sortedExercises,
        };
    }

    private groupExercisesByDueDate(): ExerciseGroups {
        const groupedExerciseGroups: ExerciseGroups = cloneDeep(DEFAULT_EXERCISE_GROUPS);
        if (!this.sortedExercises) {
            return groupedExerciseGroups;
        }

        for (const exercise of this.sortedExercises) {
            const exerciseGroup = this.getCorrespondingExerciseGroup(exercise);
            groupedExerciseGroups[exerciseGroup].entityData.push(exercise);
        }

        return groupedExerciseGroups;
    }

    private getCorrespondingExerciseGroup(exercise: Exercise): TimeGroupCategory {
        if (!exercise.dueDate) {
            return 'noDueDate';
        }

        const dueDate = dayjs(exercise.dueDate);
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

    studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.length ? exercise.studentParticipations[0] : undefined;
    }

    sortEntityData(): Exercise[] | undefined {
        const sortedEntityData = this.course?.exercises?.sort((a, b) => {
            const dueDateA = getExerciseDueDate(a, this.studentParticipation(a))?.valueOf() ?? 0;
            const dueDateB = getExerciseDueDate(b, this.studentParticipation(b))?.valueOf() ?? 0;

            if (dueDateB - dueDateA !== 0) {
                return dueDateB - dueDateA;
            }
            // If Due Date is identical or undefined sort by title
            return a.title && b.title ? a.title.localeCompare(b.title) : 0;
        });

        return sortedEntityData;
    }

    getUpcomingExercise(): Exercise | undefined {
        if (this.course) {
            const upcomingExercise = this.course.exercises?.reduce((a, b) => {
                return (a?.dueDate?.valueOf() ?? 0) > (b?.dueDate?.valueOf() ?? 0) ? a : b;
            });
            return upcomingExercise;
        }
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this.course?.exercises, true);
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }
}
