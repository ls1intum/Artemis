import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { User } from 'app/core/user/user.model';
import { IrisMessage } from 'app/entities/iris/iris-message.model';

export class IrisSession implements BaseEntity {
    id: number;
    exercise?: ProgrammingExercise;
    user?: User;
    messages?: IrisMessage[];
}
