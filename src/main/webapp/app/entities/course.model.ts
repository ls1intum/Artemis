import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { TutorGroup } from 'app/entities/tutor-group.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { Exam } from 'app/entities/exam.model';
import { Language } from 'app/entities/tutor-group.model';
import { LearningGoal } from 'app/entities/learningGoal.model';

export class Course implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public shortName?: string;
    public studentGroupName?: string;
    public teachingAssistantGroupName?: string;
    public instructorGroupName?: string;
    public startDate?: Moment;
    public endDate?: Moment;
    public semester?: string;
    public testCourse?: boolean;
    public language?: Language;
    public color?: string;
    public courseIcon?: string;
    public onlineCourse?: boolean;
    public registrationEnabled?: boolean;
    public registrationConfirmationMessage?: string;
    public presentationScore?: number;
    public maxComplaints?: number;
    public maxTeamComplaints?: number;
    public maxComplaintTimeDays?: number;
    public complaintsEnabled?: boolean;
    public studentQuestionsEnabled?: boolean;

    // the following values are only used in course administration
    public numberOfStudents?: number;
    public numberOfTeachingAssistants?: number;
    public numberOfInstructors?: number;

    public exercises?: Exercise[];
    public lectures?: Lecture[];
    public learningGoals?: LearningGoal[];
    public exams?: Exam[];
    public tutorGroups?: TutorGroup[];

    // helper attributes
    public isAtLeastTutor?: boolean;
    public isAtLeastInstructor?: boolean;
    public relativeScore?: number;
    public absoluteScore?: number;
    public maxScore?: number;

    constructor() {
        this.onlineCourse = false; // default value
        this.isAtLeastTutor = false; // default value
        this.isAtLeastInstructor = false; // default value

        this.registrationEnabled = false; // default value
        this.presentationScore = 0; // default value
        this.maxComplaints = 3; // default value
        this.maxTeamComplaints = 3; // default value
        this.maxComplaintTimeDays = 7; // default value
        this.complaintsEnabled = true; // default value
        this.studentQuestionsEnabled = true; // default value
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
                exercise.numberOfAssessments = Object.assign(new DueDateStat(), exercise.numberOfAssessments);
            });
        }
        return course;
    }
}

export const enum CourseGroup {
    STUDENTS = 'students',
    TUTORS = 'tutors',
    INSTRUCTORS = 'instructors',
}

export const courseGroups = [CourseGroup.STUDENTS, CourseGroup.TUTORS, CourseGroup.INSTRUCTORS];
