import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { EventType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export class IrisAssessment {
    public id?: number;
    public student?: User;
    public exercise?: Exercise;
    public verdict?: IrisVerdict;
    public verdictReview?: IrisVerdictReview;
    public reasoning?: string[];
    public verifiedScore?: number;
    public verifiedScoreOld?: number;
    public verifiedPoints?: number;
    public verifiedPointsOld?: number;
    public lastEvent?: EventType;
}
