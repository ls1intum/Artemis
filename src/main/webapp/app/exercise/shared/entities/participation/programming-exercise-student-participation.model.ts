import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';

export class ProgrammingExerciseStudentParticipation extends StudentParticipation {
    public repositoryUri?: string;
    public buildPlanId?: string;
    public branch?: string;

    public irisVerdict?: IrisVerdict;
    public irisVerdictReview?: IrisVerdictReview;
    public irisReasoning?: string[];
    public irisVerifiedSCore?: number;
    public irisVerifiedSCoreOld?: number;

    // helper attribute
    public buildPlanUrl?: string;
    public userIndependentRepositoryUri?: string;
    public vcsAccessToken?: string;

    constructor() {
        super(ParticipationType.PROGRAMMING);
    }
}
