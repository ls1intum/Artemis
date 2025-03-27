import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TutorParticipation } from 'app/exercise/shared/entities/participation/tutor-participation.model';

export class ExampleSubmission implements BaseEntity {
    public id?: number;

    public usedForTutorial?: boolean;
    public exercise?: Exercise;
    public submission?: Submission;
    public tutorParticipations?: TutorParticipation[];
    public assessmentExplanation?: string;
}

export enum ExampleSubmissionMode {
    READ_AND_CONFIRM = 'readConfirm',
    ASSESS_CORRECTLY = 'assessCorrectly',
}
