import { Moment } from 'moment';
import { IExerciseResults } from 'app/shared/model/exercise-results.model';
import { IParticipation } from 'app/shared/model/participation.model';
import { SubmissionType } from 'app/shared/model/enumerations/submission-type.model';

export interface ISubmission {
    id?: number;
    submitted?: boolean;
    submissionDate?: Moment;
    type?: SubmissionType;
    exampleSubmission?: boolean;
    results?: IExerciseResults[];
    participation?: IParticipation;
}

export class Submission implements ISubmission {
    constructor(
        public id?: number,
        public submitted?: boolean,
        public submissionDate?: Moment,
        public type?: SubmissionType,
        public exampleSubmission?: boolean,
        public results?: IExerciseResults[],
        public participation?: IParticipation,
    ) {
        this.submitted = this.submitted || false;
        this.exampleSubmission = this.exampleSubmission || false;
    }
}
