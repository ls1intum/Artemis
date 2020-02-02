import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';

export class TeamAssignmentConfig implements BaseEntity {
    public id: number;
    public exercise: Exercise;
    public minTeamSize = 1; // default value
    public maxTeamSize = 5; // default value

    constructor() {}
}
