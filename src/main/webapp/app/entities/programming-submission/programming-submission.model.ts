import { Submission } from '../submission';

export class ProgrammingSubmission extends Submission {
    constructor(
        public id?: number,
        public commitHash?: string,
    ) {
        super();
    }
}
