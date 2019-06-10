import { BehaviorSubject } from 'rxjs';
import { IParticipationWebsocketService, Participation } from 'app/entities/participation';
import { Exercise } from 'app/entities/exercise';
import { Result } from 'app/entities/result';

export class MockParticipationWebsocketService implements IParticipationWebsocketService {
    addParticipation = (participation: Participation, exercise?: Exercise) => {};
    addExerciseForNewParticipation = (exerciseId: number) => {};
    getAllParticipationsForExercise = (exerciseId: number) => [] as Participation[];
    subscribeForParticipationChanges = () => new BehaviorSubject<Participation>(null);
    subscribeForLatestResultOfParticipation = (participationId: number) => new BehaviorSubject<Result>(null);
}
