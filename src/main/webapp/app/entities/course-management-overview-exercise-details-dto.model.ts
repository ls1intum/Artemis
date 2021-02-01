import { Moment } from 'moment';
import { ExerciseType } from 'app/entities/exercise.model';

export class CourseManagementOverviewExerciseDetailsDTO {
    public exerciseId?: number;
    public exerciseTitle?: string;
    public exerciseType?: ExerciseType;
    public categories?: string[];
    public releaseDate?: Moment;
    public dueDate?: Moment;
    public assessmentDueDate?: Moment;
    public isTeamMode?: boolean;

    constructor() {}
}
