import { Participation } from 'app/entities/participation/participation.model';
import { EntityArrayResponseType } from 'app/exercises/shared/participation/participation.service';
import { EMPTY, Observable, of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

export class MockParticipationService {
    findWithLatestResult = (participationId: number) => of({} as Participation);
    mergeStudentParticipations = (participations: StudentParticipation[]) => participations;
    getSpecificStudentParticipation = (studentParticipations: StudentParticipation[], testRun: boolean) =>
        studentParticipations.filter((participation) => !!participation.testRun === testRun).first();

    findAllParticipationsByExercise = (exerciseId: number, withLatestResults = false): Observable<EntityArrayResponseType> => EMPTY;
}
