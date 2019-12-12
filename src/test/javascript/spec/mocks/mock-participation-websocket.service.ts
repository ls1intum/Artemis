import { BehaviorSubject } from 'rxjs';
import { IParticipationWebsocketService, Participation, StudentParticipation } from 'app/entities/participation';
import { Exercise } from 'app/entities/exercise';
import { Result } from 'app/entities/result';

export class MockParticipationWebsocketService implements IParticipationWebsocketService {
    addParticipation = (participation: Participation, exercise?: Exercise) => {};
    addExerciseForNewParticipation = (exerciseId: number) => {};
    getParticipationForExercise = (exerciseId: number) => null;
    subscribeForParticipationChanges = () => new BehaviorSubject<Participation | null>(null);
    subscribeForLatestResultOfParticipation = (participationId: number) => new BehaviorSubject<Result | null>(null);
    unsubscribeForLatestResultOfParticipation = (participationId: number) => {};
}
