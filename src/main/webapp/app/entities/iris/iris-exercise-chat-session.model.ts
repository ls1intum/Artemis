import { IrisSession } from 'app/entities/iris/iris-session.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

// TODO: Rename to IrisProgrammingExerciseChatSession
export class IrisExerciseChatSession extends IrisSession {
    exercise?: ProgrammingExercise;
}
