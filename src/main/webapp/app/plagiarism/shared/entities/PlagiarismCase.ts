import dayjs from 'dayjs/esm';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

export class PlagiarismCase {
    public id: number;
    public exercise?: PlagiarismCaseExercise;
    public post?: PlagiarismCasePostSummary;
    public plagiarismSubmissions?: PlagiarismSubmission[];
    public plagiarismSubmissionCount?: number;
    public student?: PlagiarismCaseUser;
    public verdict?: PlagiarismVerdict;
    public verdictDate?: dayjs.Dayjs;
    public verdictMessage?: string;
    public verdictBy?: PlagiarismCaseUser;
    public verdictPointDeduction?: number;
    public createdByContinuousPlagiarismControl?: boolean;
}

export class PlagiarismCaseDTO {
    public id: number;
    public verdict?: PlagiarismVerdict;
    public studentId?: number;
}

export class PlagiarismCaseUser {
    public id?: number;
    public login?: string;
    public name?: string;
    public visibleRegistrationNumber?: string;
}

export class PlagiarismCaseExercise {
    public id?: number;
    public title?: string;
    public type?: ExerciseType;
    public dueDate?: dayjs.Dayjs;
    public courseId?: number;
    public courseTitle?: string;
    public examId?: number;
    public examTitle?: string;
    public continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod?: number;
}

export class PlagiarismCasePostSummary {
    public id?: number;
    public creationDate?: dayjs.Dayjs;
    public answerAuthorIds?: number[];
}

export class PlagiarismCaseVerdictResponse {
    public verdict?: PlagiarismVerdict;
    public verdictDate?: dayjs.Dayjs;
    public verdictMessage?: string;
    public verdictBy?: PlagiarismCaseUser;
    public verdictPointDeduction?: number;
}
