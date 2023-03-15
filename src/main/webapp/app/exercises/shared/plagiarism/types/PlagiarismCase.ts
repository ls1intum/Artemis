import dayjs from 'dayjs/esm';

import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/entities/exercise.model';
import { Post } from 'app/entities/metis/post.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';

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
}
