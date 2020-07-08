import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { TutorGroup } from 'app/entities/tutor-group.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { Exam } from 'app/entities/exam.model';

export class Course implements BaseEntity {
    public id: number;
    public title: string;
    public description: string;
    public shortName: string;
    public studentGroupName: string;
    public teachingAssistantGroupName: string;
    public instructorGroupName: string;
    public startDate: Moment | null;
    public endDate: Moment | null;
    public color: string;
    public courseIcon: string;
    public onlineCourse = false; // default value
    public registrationEnabled = false; // default value
    public presentationScore = 0; // default value
    public maxComplaints = 3; // default value
    public maxTeamComplaints = 3; // default value
    public maxComplaintTimeDays = 7; // default value
    public complaintsEnabled = true; // default value
    public studentQuestionsEnabled = true; // default value

    // the following values are only used in course administration
    public numberOfStudents: number;
    public numberOfTeachingAssistants: number;
    public numberOfInstructors: number;

    public exercises: Exercise[];
    public lectures: Lecture[];
    public exams: Exam[];
    public tutorGroups: TutorGroup[];

    // helper attributes
    public isAtLeastTutor = false; // default value
    public isAtLeastInstructor = false; // default value
    public relativeScore: number;
    public absoluteScore: number;
    public maxScore: number;

    constructor() {}

    /**
     * Correctly initializes a class instance from a typecasted object.
     * Returns a 'real' class instance that supports all class methods.
     * @param object: The typecasted object
     * @returns The class instance
     */
    static from(object: Course): Course {
        const course = Object.assign(new Course(), object);
        if (course.exercises) {
            course.exercises.forEach((e) => {
                e.numberOfSubmissions = Object.assign(new DueDateStat(), e.numberOfSubmissions);
                e.numberOfAssessments = Object.assign(new DueDateStat(), e.numberOfAssessments);
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
