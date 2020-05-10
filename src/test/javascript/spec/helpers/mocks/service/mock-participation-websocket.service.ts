import { BehaviorSubject } from 'rxjs';
import { Participation } from 'app/entities/participation/participation.model';
import { IParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Exercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

export class MockParticipationWebsocketService implements IParticipationWebsocketService {
    addParticipation = (participation: Participation, exercise?: Exercise) => {};
    getParticipationForExercise = (exerciseId: number) => null;
    subscribeForParticipationChanges = () => new BehaviorSubject<Participation | null>(null);
    subscribeForLatestResultOfParticipation = (participationId: number) => new BehaviorSubject<Result | null>(null);
    unsubscribeForLatestResultOfParticipation = (participationId: number, exercise: Exercise) => {};
}
