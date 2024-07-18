import { BaseEntity } from 'app/shared/model/base-entity';

export class LearningPathsConfiguration implements BaseEntity {
    public id?: number;
    public includeAllGradedExercises?: boolean;
}
