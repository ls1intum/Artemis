import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { Exercise } from 'app/entities/exercise.model';

export class PlagiarismCase {
    public exercise: Exercise;
    public comparisons: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];
}
