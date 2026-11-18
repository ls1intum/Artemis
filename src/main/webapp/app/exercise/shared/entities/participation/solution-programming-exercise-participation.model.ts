import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

export class SolutionProgrammingExerciseParticipation extends Participation {
    public programmingExercise?: ProgrammingExercise;
    public repositoryUri?: string;
    public buildPlanId?: string;
    public buildPlanUrl?: string;

    constructor() {
        super(ParticipationType.SOLUTION);
    }
}
