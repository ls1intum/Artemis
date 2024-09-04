import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

export class SolutionProgrammingExerciseParticipation extends Participation {
    public programmingExercise?: ProgrammingExercise;
    public repositoryUri?: string;
    public buildPlanId?: string;
    public buildPlanUrl?: string;

    constructor() {
        super(ParticipationType.SOLUTION);
    }
}
