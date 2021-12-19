import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise } from 'app/entities/exercise.model';
import { BehaviorSubject } from 'rxjs';
import { Observable } from 'rxjs';

const studentExamInstance = new StudentExam();
const exercise = { id: 7 };
const exercises = [exercise];
studentExamInstance.exercises = exercises as Exercise[];

const examParticipationSubjectMock = new BehaviorSubject<StudentExam>(studentExamInstance);

export class MockExamParticipationService {
    loadStudentExamWithExercisesForSummary = (): Observable<StudentExam> => {
        return examParticipationSubjectMock;
    };

    loadStudentExamWithExercisesForConduction(courseId: number, examId: number): Observable<StudentExam> {
        return examParticipationSubjectMock;
    }

    getExamExerciseIds = (): number[] => {
        return [1, 3, 7];
    };

    saveStudentExamToLocalStorage(courseId: number, examId: number, studentExam: StudentExam): void {}
}
