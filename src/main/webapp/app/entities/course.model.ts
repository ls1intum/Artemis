import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { Exam } from 'app/entities/exam.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { Organization } from 'app/entities/organization.model';
import { Post } from 'app/entities/metis/post.model';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

export const enum Language {
    ENGLISH = 'ENGLISH',
    GERMAN = 'GERMAN',
}

export class Course implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public shortName?: string;
    public studentGroupName?: string;
    public teachingAssistantGroupName?: string;
    public editorGroupName?: string;
    public instructorGroupName?: string;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    public semester?: string;
    public testCourse?: boolean;
    public language?: Language;
    public defaultProgrammingLanguage?: ProgrammingLanguage;
    public color?: string;
    public courseIcon?: string;
    public onlineCourse?: boolean;
    public registrationEnabled?: boolean;
    public registrationConfirmationMessage?: string;
    public presentationScore?: number;
    public maxComplaints?: number;
    public maxTeamComplaints?: number;
    public maxComplaintTimeDays?: number;
    public maxComplaintTextLimit?: number;
    public maxComplaintResponseTextLimit?: number;
    public complaintsEnabled?: boolean;
    public postsEnabled?: boolean;
    public posts?: Post[];
    public requestMoreFeedbackEnabled?: boolean;
    public maxRequestMoreFeedbackTimeDays?: number;
    public maxPoints?: number;
    public accuracyOfScores?: number;

    // the following values are only used in course administration
    public numberOfStudents?: number;
    public numberOfTeachingAssistants?: number;
    public numberOfEditors?: number;
    public numberOfInstructors?: number;

    public exercises?: Exercise[];
    public lectures?: Lecture[];
    public learningGoals?: LearningGoal[];
    public prerequisites?: LearningGoal[];
    public exams?: Exam[];
    public organizations?: Organization[];

    // helper attributes
    public isAtLeastTutor?: boolean;
    public isAtLeastEditor?: boolean;
    public isAtLeastInstructor?: boolean;
    public relativeScore?: number;
    public absoluteScore?: number;
    public maxScore?: number;

    public courseArchivePath?: string;

    constructor() {
        this.onlineCourse = false; // default value
        this.isAtLeastTutor = false; // default value
        this.isAtLeastEditor = false; // default value
        this.isAtLeastInstructor = false; // default value

        this.registrationEnabled = false; // default value
        this.presentationScore = 0; // default value
        this.maxComplaints = 3; // default value
        this.maxTeamComplaints = 3; // default value
        this.maxComplaintTimeDays = 7; // default value
        this.maxComplaintTextLimit = 2000; // default value
        this.maxComplaintResponseTextLimit = 2000; // default value
        this.complaintsEnabled = true; // default value
        this.postsEnabled = true; // default value
        this.requestMoreFeedbackEnabled = true; // default value
        this.maxRequestMoreFeedbackTimeDays = 7; // default value
        this.accuracyOfScores = 1; // default value
    }

    /**
     * Correctly initializes a class instance from a typecasted object.
     * Returns a 'real' class instance that supports all class methods.
     * @param object: The typecasted object
     * @returns The class instance
     */
    static from(object: Course): Course {
        const course = Object.assign(new Course(), object);
        if (course.exercises) {
            course.exercises.forEach((exercise) => {
                exercise.numberOfSubmissions = Object.assign(new DueDateStat(), exercise.numberOfSubmissions);
                exercise.totalNumberOfAssessments = Object.assign(new DueDateStat(), exercise.totalNumberOfAssessments);
            });
        }
        return course;
    }
}

export const enum CourseGroup {
    STUDENTS = 'students',
    TUTORS = 'tutors',
    EDITORS = 'editors',
    INSTRUCTORS = 'instructors',
}

export const courseGroups = [CourseGroup.STUDENTS, CourseGroup.TUTORS, CourseGroup.EDITORS, CourseGroup.INSTRUCTORS];
