import { IrisSession } from 'app/entities/iris/iris-session.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class IrisProgrammingExerciseChatSession extends IrisSession {
    exercise?: ProgrammingExercise;
}
