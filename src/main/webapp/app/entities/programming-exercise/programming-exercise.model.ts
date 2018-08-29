import { Course } from '../course';
import { Exercise, ExerciseType } from '../exercise';

export class ProgrammingExercise extends Exercise {

    public baseRepositoryUrl: string;
    public baseBuildPlanId: string;
    public publishBuildPlanUrl = false;     //default value
    public allowOnlineEditor = false;       //default value

    constructor(course?: Course) {
        super(ExerciseType.PROGRAMMING);
        this.course = course;
        this.publishBuildPlanUrl = false;
        this.allowOnlineEditor = false;
    }
}
