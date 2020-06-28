import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseRepositoryFile } from 'app/entities/participation/ProgrammingExerciseRepositoryFile.model';

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUrl: string;
    public buildPlanId: string;
    public repositoryFiles: ProgrammingExerciseRepositoryFile[];
    public unsynchedFiles: Array<{ fileName: string; fileContent: string }> = [];

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
