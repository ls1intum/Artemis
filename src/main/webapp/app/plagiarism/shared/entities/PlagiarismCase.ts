import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

export interface PlagiarismCase {
    id: number;
    exercise?: PlagiarismCaseExercise;
    post?: PlagiarismCasePostSummary;
    plagiarismSubmissions?: PlagiarismSubmission[];
    plagiarismSubmissionCount?: number;
    student?: PlagiarismCaseUser;
    verdict?: PlagiarismVerdict;
    /** ISO 8601 date-time string as sent by the server. */
    verdictDate?: string;
    verdictMessage?: string;
    verdictBy?: PlagiarismCaseUser;
    verdictPointDeduction?: number;
    createdByContinuousPlagiarismControl?: boolean;
    hasStudentAnswer?: boolean;
}

export interface PlagiarismCaseDTO {
    id: number;
    verdict?: PlagiarismVerdict;
    studentId?: number;
}

export interface PlagiarismCaseUser {
    id?: number;
    login?: string;
    name?: string;
    visibleRegistrationNumber?: string;
}

export interface PlagiarismCaseExercise {
    id?: number;
    title?: string;
    shortName?: string;
    type?: ExerciseType;
    /** ISO 8601 date-time string as sent by the server. */
    dueDate?: string;
    courseId?: number;
    courseTitle?: string;
    examId?: number;
    examTitle?: string;
    continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod?: number;
}

export interface PlagiarismCasePostSummary {
    id?: number;
    /** ISO 8601 date-time string as sent by the server. */
    creationDate?: string;
}

export interface PlagiarismCaseVerdictResponse {
    verdict: PlagiarismVerdict;
    /** ISO 8601 date-time string as sent by the server. */
    verdictDate: string;
    verdictMessage?: string;
    verdictBy: PlagiarismCaseUser;
    verdictPointDeduction: number;
}
