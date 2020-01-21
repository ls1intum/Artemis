import { TeamParticipation } from 'app/entities/participation/team-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';

export class ProgrammingExerciseTeamParticipation extends TeamParticipation {
    public repositoryUrl: string;
    public buildPlanId: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
