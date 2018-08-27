import { Submission } from '../submission';

export class TextSubmission extends Submission {
    constructor(
        public id?: number,
        public text?: string,
    ) {
        super();
    }
}
