import { Submission, SubmissionExerciseType } from '../submission';
import { TextBlock } from 'app/entities/text-block/text-block.model';

export class TextSubmission extends Submission {
    public text: string;
    blocks?: TextBlock[];

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
