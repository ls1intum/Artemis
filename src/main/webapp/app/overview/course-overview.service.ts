import { Injectable } from '@angular/core';
import { Exercise, getIcon } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Exam } from 'app/entities/exam.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccordionGroups, SidebarCardElement, TimeGroupCategory } from 'app/types/sidebar';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';
import { faGraduationCap } from '@fortawesome/free-solid-svg-icons';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewService {
    constructor(private participationService: ParticipationService) {}

    getUpcomingTutorialGroup(tutorialGroups: TutorialGroup[] | undefined): TutorialGroup | undefined {
        if (tutorialGroups && tutorialGroups.length) {
            const upcomingTutorialGroup = tutorialGroups?.reduce((a, b) => ((a?.nextSession?.start?.valueOf() ?? 0) > (b?.nextSession?.start?.valueOf() ?? 0) ? a : b));
            return upcomingTutorialGroup;
        }
    }
    getUpcomingLecture(lectures: Lecture[] | undefined): Lecture | undefined {
        if (lectures && lectures.length) {
            const upcomingLecture = lectures?.reduce((a, b) => ((a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b));
            return upcomingLecture;
        }
    }
    getUpcomingExercise(exercises: Exercise[] | undefined): Exercise | undefined {
        if (exercises && exercises.length) {
            const upcomingLecture = exercises?.reduce((a, b) => ((a?.dueDate?.valueOf() ?? 0) > (b?.dueDate?.valueOf() ?? 0) ? a : b));
            return upcomingLecture;
        }
    }

    getUpcomingExam(exams: Exam[] | undefined): Exam | undefined {
        if (exams && exams.length) {
            const upcomingExam = exams?.reduce((a, b) => ((a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b));
            return upcomingExam;
        }
    }

    getCorrespondingGroupByDate(date: dayjs.Dayjs | undefined): TimeGroupCategory {
        if (!date) {
            return 'noDate';
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

    groupExercisesByDueDate(sortedExercises: Exercise[]): AccordionGroups {
        const groupedExerciseGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;

        for (const exercise of sortedExercises) {
            const exerciseGroup = this.getCorrespondingGroupByDate(exercise.dueDate);
            const exerciseCardItem = this.mapExerciseToSidebarCardElement(exercise);
            groupedExerciseGroups[exerciseGroup].entityData.push(exerciseCardItem);
        }

        return groupedExerciseGroups;
    }

    groupLecturesByStartDate(sortedLectures: Lecture[]): AccordionGroups {
        const groupedLectureGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;

        for (const lecture of sortedLectures) {
            const lectureGroup = this.getCorrespondingGroupByDate(lecture.startDate);
            const lectureCardItem = this.mapLectureToSidebarCardElement(lecture);
            groupedLectureGroups[lectureGroup].entityData.push(lectureCardItem);
        }

        return groupedLectureGroups;
    }

    mapLecturesToSidebarCardElements(lectures: Lecture[]) {
        return lectures.map((lecture) => this.mapLectureToSidebarCardElement(lecture));
    }
    mapTutorialGroupsToSidebarCardElements(tutorialGroups: Lecture[]) {
        return tutorialGroups.map((tutorialGroup) => this.mapTutorialGroupToSidebarCardElement(tutorialGroup));
    }

    mapExercisesToSidebarCardElements(exercises: Exercise[]) {
        return exercises.map((exercise) => this.mapExerciseToSidebarCardElement(exercise));
    }
    mapExamsToSidebarCardElements(exams: Exam[]) {
        return exams.map((exam) => this.mapExamToSidebarCardElement(exam));
    }

    mapLectureToSidebarCardElement(lecture: Lecture): SidebarCardElement {
        const lectureCardItem: SidebarCardElement = {
            title: lecture.title ?? '',
            id: lecture.id ?? '',
            subtitleLeft: lecture.startDate?.format('MMM DD, YYYY') ?? 'No date associated',
            size: 'M',
        };
        return lectureCardItem;
    }
    mapTutorialGroupToSidebarCardElement(tutorialGroup: TutorialGroup): SidebarCardElement {
        const tutorialGroupCardItem: SidebarCardElement = {
            title: tutorialGroup.title ?? '',
            id: tutorialGroup.id ?? '',
            size: 'M',
            subtitleLeft: tutorialGroup.language,
            subtitleRight: tutorialGroup.nextSession?.start?.format('MMM DD, YYYY') ? 'Next: ' + tutorialGroup.nextSession?.start?.format('MMM DD, YYYY') : 'No upcoming session',
        };
        return tutorialGroupCardItem;
    }
    mapExerciseToSidebarCardElement(exercise: Exercise): SidebarCardElement {
        const exerciseCardItem: SidebarCardElement = {
            title: exercise.title ?? '',
            id: exercise.id ?? '',
            subtitleLeft: exercise.dueDate?.format('MMM DD, YYYY') ?? 'No due date',
            type: exercise.type,
            icon: getIcon(exercise.type),
            difficulty: exercise.difficulty,
            exercise: exercise,
            studentParticipation: exercise?.studentParticipations?.length
                ? this.participationService.getSpecificStudentParticipation(exercise.studentParticipations, false)
                : undefined,
            size: 'M',
        };
        return exerciseCardItem;
    }

    mapExamToSidebarCardElement(exam: Exam): SidebarCardElement {
        const examCardItem: SidebarCardElement = {
            title: exam.title ?? '',
            id: exam.id ?? '',
            icon: faGraduationCap,
            subtitleLeft: exam.moduleNumber ?? '',
            startDateWithTime: exam.startDate,
            workingTime: this.convertWorkingTimeToString(exam.workingTime ?? 0),
            attainablePoints: exam.examMaxPoints ?? 0,
            size: 'L',
        };
        return examCardItem;
    }

    sortLectures(lectures: Lecture[]): Lecture[] {
        const sortedLecturesByStartDate = lectures.sort((a, b) => {
            const startDateA = a.startDate ? a.startDate.valueOf() : dayjs().valueOf();
            const startDateB = b.startDate ? b.startDate.valueOf() : dayjs().valueOf();
            // If Due Date is identical or undefined sort by title
            return startDateB - startDateA !== 0 ? startDateB - startDateA : this.sortByTitle(a, b);
        });

        return sortedLecturesByStartDate;
    }

    sortExercises(exercises: Exercise[]): Exercise[] {
        const sortedExercisesByDueDate = exercises?.sort((a, b) => {
            const dueDateA = getExerciseDueDate(a, this.studentParticipation(a))?.valueOf() ?? 0;
            const dueDateB = getExerciseDueDate(b, this.studentParticipation(b))?.valueOf() ?? 0;
            // If Due Date is identical or undefined sort by title
            return dueDateB - dueDateA !== 0 ? dueDateB - dueDateA : this.sortByTitle(a, b);
        });

        return sortedExercisesByDueDate;
    }
    studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.length ? exercise.studentParticipations[0] : undefined;
    }

    sortByTitle(a: Exercise | Lecture | Exam, b: Exercise | Lecture | Exam): number {
        return a.title && b.title ? a.title.localeCompare(b.title) : 0;
    }

    getSidebarCollapseStateFromStorage(storageId: string): boolean {
        const storedCollapseState: string | null = localStorage.getItem('sidebar.collapseState.' + storageId);
        return storedCollapseState ? JSON.parse(storedCollapseState) : false;
    }

    setSidebarCollapseState(storageId: string, isCollapsed: boolean) {
        localStorage.setItem('sidebar.collapseState.' + storageId, JSON.stringify(isCollapsed));
    }

    /**
     * Converts workingTime property to a formatted string to be displayed in sidebar-cards
     */
    convertWorkingTimeToString(workingTime: number): string {
        let workingTimeString = '';

        if (workingTime) {
            const hours = Math.floor(workingTime / 3600);
            const minutes = Math.floor((workingTime % 3600) / 60);
            const seconds = workingTime % 60;
            if (hours > 0) {
                workingTimeString += `${hours}h`;
                if (minutes > 0) {
                    workingTimeString += ` ${minutes} min`;
                }
            } else if (minutes > 0) {
                workingTimeString += `${minutes} min`;
                if (seconds > 0) {
                    workingTimeString += ` ${seconds} sec`;
                }
            } else {
                workingTimeString += `${seconds} sec`;
            }
        }

        return workingTimeString;
    }
}
