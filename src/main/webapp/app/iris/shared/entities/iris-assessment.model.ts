import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { User } from 'app/account/user/user.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';

export class IrisAssessment {
    public id?: number;
    public student?: User;
    public exercise?: Exercise;
    public verdict?: IrisVerdict;
    public verdictReview?: IrisVerdictReview;
    public reasoning?: string[];
    public lastEvent?: IrisPipeEvent;
}
