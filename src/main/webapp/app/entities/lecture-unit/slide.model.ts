import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/entities/exercise.model';

export class Slide implements BaseEntity {
    public id?: number;
    public slideImagePath?: string;
    public slideNumber?: number;
    public hidden?: Date;
    public exercise?: Exercise;
}
