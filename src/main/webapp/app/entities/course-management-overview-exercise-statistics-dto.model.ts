import { Moment } from 'moment';
import { ExerciseType } from 'app/entities/exercise.model';

export class CourseManagementOverviewExerciseStatisticsDTO {
    public exerciseId?: number;
    public exerciseTitle?: string;
    public exerciseType?: ExerciseType;
    public categories?: string[];
    public releaseDate?: Moment;
    public dueDate?: Moment;
    public assessmentDueDate?: Moment;
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
