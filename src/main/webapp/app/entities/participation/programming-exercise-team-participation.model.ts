import { TeamParticipation } from 'app/entities/participation/team-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseParticipation } from 'app/entities/participation/programming-exercise-participation.model';

export class ProgrammingExerciseTeamParticipation extends TeamParticipation implements ProgrammingExerciseParticipation {
    repositoryUrl: string;
    buildPlanId: string;

    constructor() {
        super(ParticipationType.PROGRAMMING_TEAM);
    }
}
