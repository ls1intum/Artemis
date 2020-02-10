import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';

export class StructuredGradingCriterion implements BaseEntity {
    id: number;
    public title: string;
    public exercise: Exercise;
}
