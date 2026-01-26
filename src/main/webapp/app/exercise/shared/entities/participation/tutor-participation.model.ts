import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { ExampleParticipation, ExampleParticipationDTO } from 'app/exercise/shared/entities/participation/example-participation.model';

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
    public trainedExampleParticipations?: ExampleParticipation[];
}

export class TutorParticipationDTO {
    id: number;
    exerciseId: number;
    tutorId: number;
    status: TutorParticipationStatus;
    trainedExampleParticipations?: ExampleParticipationDTO[];

    constructor(id: number, exerciseId: number, status: TutorParticipationStatus, tutorId: number, trainedExampleParticipations: ExampleParticipationDTO[] = []) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.status = status;
        this.tutorId = tutorId;
        this.trainedExampleParticipations = trainedExampleParticipations;
    }
}
