import { Exercise } from '../exercise';
import { Submission } from '../submission';
import { TutorParticipation } from '../tutor-participation';
import { BaseEntity } from 'app/shared';

export class ExampleSubmission implements BaseEntity {
    public id: number;

    public usedForTutorial: boolean;
    public exercise: Exercise;
    public submission: Submission;
    public tutorParticipation: TutorParticipation;

    constructor() {}
}
