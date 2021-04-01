export class CourseManagementOverviewExerciseStatisticsDTO {
    public exerciseId?: number;
    public exerciseMaxPoints?: number;
    public averageScoreInPercent?: number;
    public noOfParticipatingStudentsOrTeams?: number;
    public noOfStudentsInCourse?: number;
    public noOfTeamsInCourse?: number;
    public participationRateInPercent?: number;
    public noOfRatedAssessments?: number;
    public noOfSubmissionsInTime?: number;
    public noOfAssessmentsDoneInPercent?: number;

    constructor() {}
}
