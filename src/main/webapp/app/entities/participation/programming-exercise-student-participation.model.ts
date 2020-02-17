import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseParticipation } from 'app/entities/participation/programming-exercise-participation.model';

export class ProgrammingExerciseStudentParticipation extends StudentParticipation implements ProgrammingExerciseParticipation {
    repositoryUrl: string;
    buildPlanId: string;

    constructor() {
        super(ParticipationType.PROGRAMMING_STUDENT);
    }
}
