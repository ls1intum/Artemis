import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';

export class IrisExerciseChatSession extends IrisSession {
    exercise?: ProgrammingExercise;
}
