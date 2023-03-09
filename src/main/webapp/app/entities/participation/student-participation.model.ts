import { User } from 'app/core/user/user.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { Team } from 'app/entities/team.model';

export class StudentParticipation extends Participation {
    public student?: User;
    public team?: Team;
    public testRun?: boolean;

    constructor(type?: ParticipationType) {
        super(type ?? ParticipationType.STUDENT);
    }
}
