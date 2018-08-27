import { Submission } from '../submission';

export class ModelingSubmission extends Submission {

    public model: string;
    public explanationText: string;

    constructor() {
        super();
    }
}
