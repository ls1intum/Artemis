import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';

export class GradingCriterion implements BaseEntity {
    id: number;
    public title: string;
    public exercise: Exercise;
}
