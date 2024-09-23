import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUri?: string;
    public buildPlanId?: string;
    public branch?: string;
    public locked?: boolean;

    // helper attribute
    public buildPlanUrl?: string;
    public userIndependentRepositoryUri?: string;
    public vcsAccessToken?: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
