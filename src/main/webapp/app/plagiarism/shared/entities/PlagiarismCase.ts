import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { Post } from 'app/communication/shared/entities/post.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

export class PlagiarismCase {
    public id: number;
    public exercise?: Exercise;
    public post?: Post;
    public plagiarismSubmissions?: PlagiarismSubmission[];
    public student?: User;
    public verdict?: PlagiarismVerdict;
    public verdictDate?: dayjs.Dayjs;
    public verdictMessage?: string;
    public verdictBy?: User;
    public verdictPointDeduction?: number;
    public createdByContinuousPlagiarismControl?: boolean;
}
