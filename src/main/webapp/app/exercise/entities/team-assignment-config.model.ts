import { BaseEntity } from 'app/shared/model/base-entity';
import { Exercise } from 'app/exercise/entities/exercise.model';

export class TeamAssignmentConfig implements BaseEntity {
    public id?: number;
    public exercise?: Exercise;
    public minTeamSize?: number;
    public maxTeamSize?: number;

    constructor() {
        this.minTeamSize = 1; // default value
        this.maxTeamSize = 5; // default value
    }
}
