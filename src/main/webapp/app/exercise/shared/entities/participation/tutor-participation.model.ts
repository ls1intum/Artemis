import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExampleSubmission, ExampleSubmissionDTO } from 'app/assessment/shared/entities/example-submission.model';

export const enum TutorParticipationStatus {
    NOT_PARTICIPATED = 'NOT_PARTICIPATED',
    REVIEWED_INSTRUCTIONS = 'REVIEWED_INSTRUCTIONS',
    TRAINED = 'TRAINED',
    COMPLETED = 'COMPLETED',
}

export class TutorParticipation implements BaseEntity {
    public id?: number;

    public status?: TutorParticipationStatus;
    public assessedExercise?: Exercise;
    public tutor?: User;
    public trainedExampleSubmissions?: ExampleSubmission[];
}

export class TutorParticipationDTO {
    id: number;
    exerciseId: number;
    tutorId: number;
    status: TutorParticipationStatus;
    trainedExampleSubmissions?: ExampleSubmissionDTO[];

    constructor(id: number, exerciseId: number, status: TutorParticipationStatus, tutorId: number, trainedExampleSubmissions: ExampleSubmissionDTO[] = []) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.status = status;
        this.tutorId = tutorId;
        this.trainedExampleSubmissions = trainedExampleSubmissions;
    }
}
