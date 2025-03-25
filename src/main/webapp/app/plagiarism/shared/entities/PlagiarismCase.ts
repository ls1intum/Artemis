import { TextSubmissionElement } from 'app/plagiarism/shared/entities/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/plagiarism/shared/entities/modeling/ModelingSubmissionElement';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { Post } from 'app/entities/metis/post.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

export class PlagiarismCase {
    public id: number;
    public exercise?: Exercise;
    public post?: Post;
    public plagiarismSubmissions?: PlagiarismSubmission<TextSubmissionElement | ModelingSubmissionElement>[];
    public student?: User;
    public verdict?: PlagiarismVerdict;
    public verdictDate?: dayjs.Dayjs;
    public verdictMessage?: string;
    public verdictBy?: User;
    public verdictPointDeduction?: number;
    public createdByContinuousPlagiarismControl?: boolean;
}
