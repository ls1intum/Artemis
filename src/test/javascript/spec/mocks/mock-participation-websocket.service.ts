import { BehaviorSubject } from 'rxjs';
import { IParticipationWebsocketService, Participation } from 'app/entities/participation';
import { Exercise } from 'app/entities/exercise';
import { Result } from 'app/entities/result';

export class MockParticipationWebsocketService implements IParticipationWebsocketService {
    addExerciseForNewParticipation = (exerciseId: number) => {};
    addParticipation = (participation: Participation, exercise?: Exercise) => {};
    getAllParticipations = () => [];
    getAllParticipationsForExercise = (exerciseId: number) => [];
    getParticipation = (id: number) => ({} as Participation);
    removeParticipation = (id: number, exerciseId?: number) => {};
    setCachedParticipation = (participations: Participation[], exercise?: Exercise) => {};
    subscribeForLatestResultOfParticipation = (participationId: number) => new BehaviorSubject<Result>(null);
    subscribeForParticipationChanges = () => new BehaviorSubject<Participation>(null);
    updateParticipation = (participation: Participation, exercise?: Exercise) => {};
}
