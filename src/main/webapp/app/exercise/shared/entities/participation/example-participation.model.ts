import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { TutorParticipation } from 'app/exercise/shared/entities/participation/tutor-participation.model';

export class ExampleParticipation extends Participation {
    public usedForTutorial?: boolean;
    public assessmentExplanation?: string;
    public tutorParticipations?: TutorParticipation[];

    constructor() {
        super(ParticipationType.EXAMPLE);
    }
}

export enum ExampleSubmissionMode {
    READ_AND_CONFIRM = 'readConfirm',
    ASSESS_CORRECTLY = 'assessCorrectly',
}

export class ExampleParticipationDTO {
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
