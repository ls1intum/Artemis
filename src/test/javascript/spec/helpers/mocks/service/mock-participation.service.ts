import { Participation } from 'app/entities/participation/participation.model';
import { of } from 'rxjs';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
}
