import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BehaviorSubject } from 'rxjs';
import { Observable } from 'rxjs';

let studentExam = new StudentExam();
let exercise = { id: 7 };
let exercises = [exercise];
studentExam.exercises = exercises as Exercise[];

const examParticipationSubjectMock = new BehaviorSubject<StudentExam>(studentExam);

export class MockExamParticipationService {
    loadStudentExamWithExercisesForSummary = (): Observable<StudentExam> => {
        return examParticipationSubjectMock;
    };
}
