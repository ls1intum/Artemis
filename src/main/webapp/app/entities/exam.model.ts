import { ExamUser } from 'app/entities/exam-user.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { faCheck, faCircleExclamation, faHourglassHalf, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export enum ExamStatus {
    NOT_STARTED = 'notStarted',
    ONGOING = 'ongoing',
    FINISHED = 'finished',
}

export class Exam implements BaseEntity {
    public id?: number;
    public title?: string;
    public testExam?: boolean;
    public examWithAttendanceCheck?: boolean;
    public visibleDate?: dayjs.Dayjs;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    // Default exam working time in seconds
    public workingTime?: number;
    public publishResultsDate?: dayjs.Dayjs;
    public examStudentReviewStart?: dayjs.Dayjs;
    public examStudentReviewEnd?: dayjs.Dayjs;
    public exampleSolutionPublicationDate?: dayjs.Dayjs;
    // grace period in seconds - time in which students can still submit even though working time is over
    public gracePeriod?: number;
    public examiner?: string;
    public moduleNumber?: string;
    public courseName?: string;

    public startText?: string;
    public endText?: string;
    public confirmationStartText?: string;
    public confirmationEndText?: string;
    public examMaxPoints?: number;
    public randomizeExerciseOrder?: boolean;
    public numberOfExercisesInExam?: number;
    public numberOfCorrectionRoundsInExam?: number;
    public course?: Course;
    public exerciseGroups?: ExerciseGroup[];
    public studentExams?: StudentExam[];
    public examUsers?: ExamUser[];
    public quizExamMaxPoints?: number;
    public numberOfExamUsers?: number; // transient
    public channelName?: string; // transient

    // helper attributes
    public visible?: boolean;
    public started?: boolean;

    public examArchivePath?: string;

    public latestIndividualEndDate?: dayjs.Dayjs;

    constructor() {
        this.randomizeExerciseOrder = false; // default value (set by server)
        this.numberOfCorrectionRoundsInExam = 1; // default value
        this.examMaxPoints = 1; // default value
        this.workingTime = 0; // will be updated during creation
        this.testExam = false; // default value
        this.examWithAttendanceCheck = false; // default value

        // helper attributes (calculated by the server at the time of the last request)
        this.visible = false;
        this.started = false;
    }
}

// THIS PART WILL BE NEEDED IN THE FOLLOW UP PRs WHEN STATUS ICONS FOR EXAMS WILL BE IMPLEMENTED
/**
 * Determine the status of an exam based on its start and end dates.
 * @param exam {Exam}
 * @returns {ExamStatus}
 */
export function determineExamStatus(exam: Exam): ExamStatus {
    const now = dayjs();
    if (!exam.started) {
        return ExamStatus.NOT_STARTED;
    } else if (exam.endDate && now.isAfter(exam.endDate)) {
        return ExamStatus.FINISHED;
    } else if (exam.startDate && exam.endDate && now.isAfter(exam.startDate) && now.isBefore(exam.endDate)) {
        return ExamStatus.ONGOING;
    }
    return ExamStatus.ONGOING; // Default case if dates are not properly set
}

// THIS PART WILL BE NEEDED IN THE FOLLOW UP PRs WHEN STATUS ICONS FOR EXAMS WILL BE IMPLEMENTED
/**
 * Get an icon for the given exam status.
 * @param status {ExamStatus}
 * @returns {IconProp}
 */
export function getExamStatusIcon(status?: ExamStatus): IconProp {
    if (!status) {
        return faQuestion as IconProp; // Default icon if no status is provided
    }

    const statusIcons = {
        [ExamStatus.NOT_STARTED]: faHourglassHalf,
        [ExamStatus.ONGOING]: faCircleExclamation,
        [ExamStatus.FINISHED]: faCheck,
    };

    return statusIcons[status] as IconProp;
}

// THIS PART WILL BE NEEDED IN THE FOLLOW UP PRs WHEN STATUS ICONS FOR EXAMS WILL BE IMPLEMENTED
/**
 * Get the appropriate icon for an exam based on its current status.
 * @param exam {Exam}
 * @returns {IconProp}
 */
export function getIconForExam(exam: Exam): IconProp {
    const status = determineExamStatus(exam);
    return getExamStatusIcon(status);
}

// THIS PART WILL BE NEEDED IN THE FOLLOW UP PRs WHEN STATUS ICONS FOR EXAMS WILL BE IMPLEMENTED
/**
 * Get the color for an icon based on the exam's current status.
 * @param exam {Exam}
 * @returns {string} The color corresponding to the exam status.
 */
export function getColorForIcon(exam: Exam): string {
    const status = determineExamStatus(exam);
    const statusColors = {
        [ExamStatus.NOT_STARTED]: 'var(--bs-body-color)',
        [ExamStatus.ONGOING]: 'var(--red)',
        [ExamStatus.FINISHED]: 'var(--green)',
    };

    return statusColors[status];
}
