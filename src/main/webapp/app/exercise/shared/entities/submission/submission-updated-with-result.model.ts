import { Submission } from 'app/exercise/shared/entities/submission/submission.model';

export interface SubmissionUpdateResult {
    updatedSubmissions: Submission[];
    hasMatchingSubmission: boolean;
}
