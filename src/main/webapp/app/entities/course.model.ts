import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { Exam } from 'app/entities/exam/exam.model';
import { Competency } from 'app/entities/competency.model';
import { Organization } from 'app/entities/organization.model';
import { Post } from 'app/entities/metis/post.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { LearningPath } from 'app/entities/competency/learning-path.model';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { Faq } from 'app/entities/faq.model';

export enum CourseInformationSharingConfiguration {
    COMMUNICATION_AND_MESSAGING = 'COMMUNICATION_AND_MESSAGING',
    COMMUNICATION_ONLY = 'COMMUNICATION_ONLY',
    DISABLED = 'DISABLED',
}

/**
 * Note: Keep in sync with method in CourseRepository.java
 */
export function isCommunicationEnabled(course: Course | undefined) {
    const config = course?.courseInformationSharingConfiguration;
    return config === CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING || config === CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
}

/**
 * Note: Keep in sync with method in CourseRepository.java
 */
export function isMessagingEnabled(course: Course | undefined) {
    const config = course?.courseInformationSharingConfiguration;
    return config === CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
}

export const enum Language {
    ENGLISH = 'ENGLISH',
    GERMAN = 'GERMAN',
}
export class Course implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public studentCourseAnalyticsDashboardEnabled?: boolean;
    public shortName?: string;
    public studentGroupName?: string;
    public teachingAssistantGroupName?: string;
    public editorGroupName?: string;
    public instructorGroupName?: string;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    public enrollmentStartDate?: dayjs.Dayjs;
    public enrollmentEndDate?: dayjs.Dayjs;
    public unenrollmentEndDate?: dayjs.Dayjs;
    public semester?: string;
    public testCourse?: boolean;
    public language?: Language;
    public defaultProgrammingLanguage?: ProgrammingLanguage;
    public color?: string;
    public courseIcon?: string;
    public onlineCourse?: boolean;
    public faqEnabled?: boolean;
    public enrollmentEnabled?: boolean;
    public enrollmentConfirmationMessage?: string;
    public unenrollmentEnabled?: boolean;
    public presentationScore?: number;
    public maxComplaints?: number;
    public maxTeamComplaints?: number;
    public maxComplaintTimeDays?: number;
    public maxComplaintTextLimit?: number;
    public maxComplaintResponseTextLimit?: number;
    public complaintsEnabled?: boolean;
    public posts?: Post[];
    public requestMoreFeedbackEnabled?: boolean;
    public maxRequestMoreFeedbackTimeDays?: number;
    public maxPoints?: number;
    public accuracyOfScores?: number;
    public restrictedAthenaModulesAccess?: boolean;
    public tutorialGroupsConfiguration?: TutorialGroupsConfiguration;
    // Note: Currently just used in the scope of the tutorial groups feature
    public timeZone?: string;

    // the following values are only used in course administration
    public numberOfStudents?: number;
    public numberOfTeachingAssistants?: number;
    public numberOfEditors?: number;
    public numberOfInstructors?: number;

    // helper attributes to determine if certain tabs in the client are shown
    public numberOfLectures?: number;
    public numberOfExams?: number;
    public numberOfTutorialGroups?: number;
    public numberOfCompetencies?: number;
    public numberOfPrerequisites?: number;

    public exercises?: Exercise[];
    public lectures?: Lecture[];
    public faqs?: Faq[];
    public competencies?: Competency[];
    public prerequisites?: Prerequisite[];
    public learningPathsEnabled?: boolean;
    public learningPaths?: LearningPath[];
    public exams?: Exam[];
    public organizations?: Organization[];
    public tutorialGroups?: TutorialGroup[];
    public onlineCourseConfiguration?: OnlineCourseConfiguration;
    public courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
    public courseInformationSharingMessagingCodeOfConduct?: string;

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

        this.enrollmentEnabled = false; // default value
        this.presentationScore = 0; // default value
        this.maxComplaints = 3; // default value
        this.maxTeamComplaints = 3; // default value
        this.maxComplaintTimeDays = 7; // default value
        this.maxComplaintTextLimit = 2000; // default value
        this.maxComplaintResponseTextLimit = 2000; // default value
        this.complaintsEnabled = true; // default value
        this.requestMoreFeedbackEnabled = true; // default value
        this.maxRequestMoreFeedbackTimeDays = 7; // default value
        this.accuracyOfScores = 1; // default value
        this.restrictedAthenaModulesAccess = false; // default value
        this.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING; // default value
    }

    /**
     * Correctly initializes a class instance from a typecasted object.
     * Returns a 'real' class instance that supports all class methods.
     * @param object The typecasted object
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

export class CourseForImportDTO {
    id?: number;
    title?: string;
    shortName?: string;
    semester?: string;

    constructor() {}
}

export const enum CourseGroup {
    STUDENTS = 'students',
    TUTORS = 'tutors',
    EDITORS = 'editors',
    INSTRUCTORS = 'instructors',
}

export const courseGroups = [CourseGroup.STUDENTS, CourseGroup.TUTORS, CourseGroup.EDITORS, CourseGroup.INSTRUCTORS];
