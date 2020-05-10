import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/entities/exercise.model';

export class TeamAssignmentConfig implements BaseEntity {
    public id: number;
    public exercise: Exercise;
    public minTeamSize = 1; // default value
    public maxTeamSize = 5; // default value

    constructor() {}
}
