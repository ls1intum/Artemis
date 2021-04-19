import { Moment } from 'moment';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Exercise } from 'app/entities/exercise.model';

export class CourseManagementDetailViewDto {
    id: number;
    presentationScore: number;
    semester: string;
    startDate: Moment;
    endDate: Moment;
    description: string;
    courseIcon: string;
    title: string;
    testCourse: boolean;
    shortName: string;
    color: string;

    studentGroupName: string;
    teachingAssistantGroupName: string;
    instructorGroupName: string;
    numberOfStudentsInCourse: number;
    numberOfTeachingAssistantsInCourse: number;
    numberOfInstructorsInCourse: number;

    // helper && temporal variables
    isAtLeastInstructor: boolean;
    courseArchivePath?: string;
    studentQuestionsEnabled?: boolean;
    isAtLeastTutor?: boolean;
    registrationEnabled?: boolean;

    // Total Assessment
    currentPercentageAssessments: number;
    currentAbsoluteAssessments: number;
    currentMaxAssessments: number;

    // Total Complaints
    currentPercentageComplaints: number;
    currentAbsoluteComplaints: number;
    currentMaxComplaints: number;

    // More Feedback Request
    currentPercentageMoreFeedbacks: number;
    currentAbsoluteMoreFeedbacks: number;
    currentMaxMoreFeedbacks: number;

    // Average Student Score
    currentPercentageAverageScore: number;
    currentAbsoluteAverageScore: number;
    currentMaxAverageScore: number;

    activeStudents: number[];

    exerciseDetails: Exercise[];
    exercisesStatistics: CourseManagementOverviewExerciseStatisticsDTO[];

    constructor() {}
}
