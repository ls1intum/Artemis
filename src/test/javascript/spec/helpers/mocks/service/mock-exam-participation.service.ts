import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BehaviorSubject } from 'rxjs';
import { Observable } from 'rxjs';

const studentExam = new StudentExam();
const exercise = { id: 7 };
const exercises = [exercise];
studentExam.exercises = exercises as Exercise[];

const examParticipationSubjectMock = new BehaviorSubject<StudentExam>(studentExam);

export class MockExamParticipationService {
    loadStudentExamWithExercisesForSummary = (): Observable<StudentExam> => {
        return examParticipationSubjectMock;
    };

    getExamExerciseIds = (): number[] => {
        return [1, 3, 7];
    };
}
