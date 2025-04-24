import { User } from 'app/core/user/user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';

export class StudentParticipation extends Participation {
    public student?: User;
    public team?: Team;
    public testRun?: boolean;

    constructor(type?: ParticipationType) {
        super(type ?? ParticipationType.STUDENT);
    }
}

/**
 * Checks if the participation is used for practicing in a course exercise. This is the case if testRun is set to true
 * @param studentParticipation the participation to check
 */
export function isPracticeMode(studentParticipation: StudentParticipation | undefined): boolean | undefined {
    return studentParticipation?.testRun;
}

/**
 * Stores whether the participation is used for practicing in a course exercise.
 * @param studentParticipation the participation that should store if it is used for practicing
 * @param practiceMode true, if it is used for practicing
 */
export function setPracticeMode(studentParticipation: StudentParticipation | undefined, practiceMode: boolean) {
    if (studentParticipation) {
        studentParticipation.testRun = practiceMode;
    }
}
