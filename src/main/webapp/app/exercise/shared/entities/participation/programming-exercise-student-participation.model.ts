import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUri?: string;
    public buildPlanId?: string;
    public branch?: string;
    public irisAssessment?: IrisAssessment;

    // helper attribute
    public buildPlanUrl?: string;
    public userIndependentRepositoryUri?: string;
    public vcsAccessToken?: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
