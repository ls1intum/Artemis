import { Exercise } from '../exercise';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { Team } from 'app/entities/team/team.model';

export class TeamParticipation extends Participation {
    public team: Team;
    public exercise: Exercise;

    constructor(type?: ParticipationType) {
        super(type ? type : ParticipationType.TEAM);
    }

    getExercise(): Exercise {
        return this.exercise;
    }
}
