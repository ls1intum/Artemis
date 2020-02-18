import { User } from 'app/core/user/user.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { Exercise } from 'app/entities/exercise/exercise.model';

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
