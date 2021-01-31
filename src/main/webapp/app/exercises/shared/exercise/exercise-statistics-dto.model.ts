export class CourseExerciseStatisticsDTO {
    public exerciseId?: number;
    public exerciseTitle?: string;
    public exerciseMode?: string;
    public exerciseMaxPoints?: number;
    public averageScoreInPercent?: number;
    public noOfParticipatingStudentsOrTeams?: number;
    public noOfStudentsInCourse?: number;
    public noOfTeamsInCourse?: number;
    public participationRateInPercent?: number;

    constructor() {}
}
