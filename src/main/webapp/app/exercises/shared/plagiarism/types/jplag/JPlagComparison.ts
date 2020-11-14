import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { JPlagMatch } from 'app/exercises/shared/plagiarism/types/jplag/JPlagMatch';
import { JPlagSubmission } from 'app/exercises/shared/plagiarism/types/jplag/JPlagSubmission';

export interface JPlagComparison extends PlagiarismComparison {
    matches: JPlagMatch[];
    subA: JPlagSubmission;
    subB: JPlagSubmission;
    numberOfMatchedTokens: number;
    bcMatchesA: any;
    bcMatchesB: any;
}
