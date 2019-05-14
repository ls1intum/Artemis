import { Submission, SubmissionExerciseType } from '../submission';
import { Language } from 'app/entities/tutor-group';

export class TextSubmission extends Submission {
    public text: string;
    public language: Language;

    constructor() {
        super(SubmissionExerciseType.TEXT);
    }
}
