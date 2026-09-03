import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';

/** The effort a participant reports for a user story exercise, in hours. Either value may be unset. */
export interface UserStoryEffort {
    estimatedEffort?: number;
    actualEffort?: number;
}

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUri?: string;
    public buildPlanId?: string;
    public branch?: string;

    // helper attribute
    public buildPlanUrl?: string;
    public userIndependentRepositoryUri?: string;
    public vcsAccessToken?: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
