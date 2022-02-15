import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUrl?: string;
    public buildPlanId?: string;

    // helper attribute
    public buildPlanUrl?: string;
    public userIndependentRepositoryUrl?: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
