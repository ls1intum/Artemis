import { User } from 'app/core/user/user.model';
import { Team } from 'app/entities/team.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { Exercise } from 'app/entities/exercise.model';

export class StudentParticipation extends Participation {
    public student: User;
    public team: Team;
    public exercise: Exercise;

    constructor(type?: ParticipationType) {
        super(type ? type : ParticipationType.STUDENT);
    }
}
