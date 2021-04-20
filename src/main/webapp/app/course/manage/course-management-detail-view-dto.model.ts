import { Course } from 'app/entities/course.model';

export class CourseManagementDetailViewDto {
    course: Course;
    numberOfStudentsInCourse: number;
    numberOfTeachingAssistantsInCourse: number;
    numberOfInstructorsInCourse: number;

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

    constructor() {}
}
