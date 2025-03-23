import { User } from 'app/core/user/user.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/exercise/entities/exercise.model';
import { ExampleSubmission } from 'app/exercise/entities/example-submission.model';

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
