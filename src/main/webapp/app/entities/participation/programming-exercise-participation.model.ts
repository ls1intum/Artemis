import { Participation } from 'app/entities/participation/participation.model';

export interface ProgrammingExerciseParticipation extends Participation {
    repositoryUrl: string;
    buildPlanId: string;
}
