import { Course } from '../course';
import { Exercise, ExerciseType } from '../exercise';
import { Participation } from 'app/entities/participation';

export const enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON'
}

export class ProgrammingExercise extends Exercise {

    public templateParticipation: Participation;
    public solutionParticipation: Participation;
    public testRepositoryUrl: string;
    public publishBuildPlanUrl = false; // default value
    public allowOnlineEditor = false; // default value
    public programmingLanguage = ProgrammingLanguage.JAVA; // default value
    public packageName: string;

    constructor(course?: Course) {
        super(ExerciseType.PROGRAMMING);
        this.course = course;
        this.templateParticipation = new Participation();
        this.solutionParticipation = new Participation();
    }
}
