import { BehaviorSubject } from 'rxjs';
import { Observable } from 'rxjs';

const exerciseIds = [42, 7, 13];

const examExerciseIdsSubjectMock = new BehaviorSubject<number[]>(exerciseIds);

export class MockExamParticipationService {
    loadStudentExamExerciseIds = (): Observable<number[]> => {
        return examExerciseIdsSubjectMock;
    };
}
