import { IrisSession } from 'app/entities/iris/iris-session.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

export class IrisExerciseChatSession extends IrisSession {
    exercise?: ProgrammingExercise;
}
