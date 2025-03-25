import { IrisSession } from 'app/entities/iris/iris-session.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

export class IrisExerciseChatSession extends IrisSession {
    exercise?: ProgrammingExercise;
}
