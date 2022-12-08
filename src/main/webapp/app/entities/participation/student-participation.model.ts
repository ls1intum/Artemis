import { User } from 'app/core/user/user.model';
import { Team } from 'app/entities/team.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';

export class StudentParticipation extends Participation {
    public student?: User;
    public team?: Team;
    public participantIdentifier?: string;
    public testRun?: boolean;
    public participationResult?: Result;

    constructor(type?: ParticipationType) {
        super(type ?? ParticipationType.STUDENT);
    }
}
