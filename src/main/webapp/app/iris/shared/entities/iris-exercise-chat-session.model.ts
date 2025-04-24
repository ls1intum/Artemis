import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

export class IrisExerciseChatSession extends IrisSession {
    exercise?: ProgrammingExercise;
}
