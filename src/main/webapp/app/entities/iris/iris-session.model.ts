import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/entities/exercise.model';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class IrisSession implements BaseEntity {
    public id?: number;
    public user?: User;
    public programmingExercise?: Exercise;
    public messages?: IrisMessage[];
}
