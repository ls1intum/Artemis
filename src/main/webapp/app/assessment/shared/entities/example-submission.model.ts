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

export class ExampleSubmissionDTO {
    public id: number;
    public usedForTutorial: boolean;
    public submissionId: number;
    public assessmentExplanation?: string;

    constructor(id: number, usedForTutorial: boolean, submissionId: number, assessmentExplanation?: string) {
        this.id = id;
        this.usedForTutorial = usedForTutorial;
        this.submissionId = submissionId;
        this.assessmentExplanation = assessmentExplanation;
    }
}
