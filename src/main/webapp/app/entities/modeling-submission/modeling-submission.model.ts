import { Submission } from '../submission';

export class ModelingSubmission extends Submission {
    constructor(
        public id?: number,
        public model?: string,
        public submitted?: boolean,
    ) {
        super();
    }
}
