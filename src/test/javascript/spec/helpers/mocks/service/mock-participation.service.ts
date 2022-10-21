import { Participation } from 'app/entities/participation/participation.model';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
    mergeStudentParticipations = (participations: StudentParticipation[]) => participations;
}
