import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/entities/exercise/exercise.model';
import { Submission } from 'app/entities/submission/submission.model';
import { TutorParticipation } from 'app/entities/tutor-participation/tutor-participation.model';

export class ExampleSubmission implements BaseEntity {
    public id: number;

    public usedForTutorial: boolean;
    public exercise: Exercise;
    public submission: Submission;
    public tutorParticipations: TutorParticipation[];
    public assessmentExplanation: string;

    constructor() {}
}
