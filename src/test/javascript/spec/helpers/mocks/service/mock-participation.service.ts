import { Participation } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { of } from 'rxjs';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
    mergeStudentParticipations = (participations: StudentParticipation[]) => participations;
}
