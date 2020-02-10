import { User } from 'app/core/user/user.model';
import { Exercise } from '../exercise';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';

export class StudentParticipation extends Participation {
    public student: User;
    public exercise: Exercise;

    constructor(type?: ParticipationType) {
        super(type ? type : ParticipationType.STUDENT);
    }

    getExercise(): Exercise {
        return this.exercise;
    }
}
