import { Injectable } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { ExerciseGroups, LectureGroups, TimeGroupCategory } from 'app/types/sidebar';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';

export type UnitGroups = LectureGroups | ExerciseGroups;
const DEFAULT_UNIT_GROUPS: LectureGroups | ExerciseGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDueDate: { entityData: [] },
};

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewService {
    constructor() {}

    // getUpcomingUnit(unit: Exercise[] | Lecture[], typeOfDate: string): Exercise | Lecture | undefined {
    //     if (!unit) {
    //         return;
    //     }
    //     return unit.reduce((a, b) => ((a[typeOfDate]?.valueOf() ?? 0) > (b[typeOfDate]?.valueOf() ?? 0) ? a : b));
    // }

    getUpcomingLecture(lectures: Lecture[] | undefined): Lecture | undefined {
        if (lectures) {
            const upcomingLecture = lectures?.reduce((a, b) => ((a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b));
            return upcomingLecture;
        }
    }
    getUpcomingExercise(exercise: Exercise[] | undefined): Exercise | undefined {
        if (exercise) {
            const upcomingLecture = exercise?.reduce((a, b) => ((a?.dueDate?.valueOf() ?? 0) > (b?.dueDate?.valueOf() ?? 0) ? a : b));
            return upcomingLecture;
        }
    }

    getCorrespondingGroupByDate(date: dayjs.Dayjs | undefined): TimeGroupCategory {
        if (!date) {
            return 'noDueDate';
        }

        const dueDate = dayjs(date);
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

    groupExercisesByDueDate(sortedExercises: Exercise[]): ExerciseGroups {
        const groupedExerciseGroups: ExerciseGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as ExerciseGroups;

        for (const exercise of sortedExercises) {
            const exerciseGroup = this.getCorrespondingGroupByDate(exercise.dueDate);
            groupedExerciseGroups[exerciseGroup].entityData.push(exercise);
        }

        return groupedExerciseGroups;
    }

    groupLecturesByStartDate(sortedLectures: Lecture[]): LectureGroups {
        const groupedLectureGroups: LectureGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as LectureGroups;

        for (const lecture of sortedLectures) {
            const lectureGroup = this.getCorrespondingGroupByDate(lecture.startDate);
            groupedLectureGroups[lectureGroup].entityData.push(lecture);
        }

        return groupedLectureGroups;
    }

    sortLectures(lectures: Lecture[]): Lecture[] {
        const sortedLecturesByStartDate = lectures.sort((a, b) => {
            const startDateA = a.startDate ? a.startDate.valueOf() : dayjs().valueOf();
            const startDateB = b.startDate ? b.startDate.valueOf() : dayjs().valueOf();
            // If Due Date is identical or undefined sort by title
            return startDateB - startDateA ?? this.sortByTitle(a, b);
        });

        return sortedLecturesByStartDate;
    }

    sortExercises(exercises: Exercise[]): Exercise[] {
        const sortedExercisesByDueDate = exercises?.sort((a, b) => {
            const dueDateA = getExerciseDueDate(a, this.studentParticipation(a))?.valueOf() ?? 0;
            const dueDateB = getExerciseDueDate(b, this.studentParticipation(b))?.valueOf() ?? 0;

            // If Due Date is identical or undefined sort by title
            return dueDateB - dueDateA ?? this.sortByTitle(a, b);
        });

        return sortedExercisesByDueDate;
    }
    studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.length ? exercise.studentParticipations[0] : undefined;
    }

    sortByTitle(a: Exercise | Lecture, b: Exercise | Lecture): number {
        return a.title && b.title ? a.title.localeCompare(b.title) : 0;
    }
}
