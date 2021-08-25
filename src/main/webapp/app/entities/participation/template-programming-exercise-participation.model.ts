import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class TemplateProgrammingExerciseParticipation extends Participation {
    public programmingExercise?: ProgrammingExercise;
    public repositoryUrl?: string;
    public buildPlanId?: string;
    public buildPlanUrl?: string;

    constructor() {
        super(ParticipationType.TEMPLATE);
    }
}
