import { Participation } from 'app/entities/participation';
import { of } from 'rxjs';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
}
