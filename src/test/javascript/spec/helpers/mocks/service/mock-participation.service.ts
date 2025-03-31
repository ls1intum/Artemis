import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { EntityArrayResponseType } from 'app/exercise/participation/participation.service';
import { EMPTY, Observable, of } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
    mergeStudentParticipations = (participations: StudentParticipation[]) => participations;
    getSpecificStudentParticipation = (studentParticipations: StudentParticipation[], testRun: boolean) =>
        studentParticipations.filter((participation) => !!participation.testRun === testRun).first();

    findAllParticipationsByExercise = (exerciseId: number, withLatestResults = false): Observable<EntityArrayResponseType> => EMPTY;
}
