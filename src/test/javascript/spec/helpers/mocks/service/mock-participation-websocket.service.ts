import { BehaviorSubject } from 'rxjs';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { IParticipationWebsocketService } from 'app/core/course/shared/participation-websocket.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

export class MockParticipationWebsocketService implements IParticipationWebsocketService {
    addParticipation = (participation: Participation, exercise?: Exercise) => {};
    getParticipationsForExercise = (exerciseId: number) => undefined;
    subscribeForParticipationChanges = () => new BehaviorSubject<Participation | undefined>(undefined);
    subscribeForLatestResultOfParticipation = (participationId: number) => new BehaviorSubject<Result | undefined>(undefined);
    unsubscribeForLatestResultOfParticipation = (participationId: number, exercise: Exercise) => {};
    notifyAllResultSubscribers = (result: Result) => {};
}
