import { Exercise } from 'app/entities/exercise.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { BehaviorSubject, Observable, of } from 'rxjs';

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

    loadStudentExamWithExercisesForConductionOfTestExam(courseId: number, examId: number, studentExamId: number): Observable<StudentExam> {
        return examParticipationSubjectMock;
    }

    loadStudentExamGradeInfoForSummary(courseId: number, examId: number, userId?: number): Observable<StudentExamWithGradeDTO> {
        return of({} as StudentExamWithGradeDTO);
    }

    getExamExerciseIds = (): number[] => {
        return [1, 3, 7];
    };

    saveStudentExamToLocalStorage(courseId: number, examId: number, studentExam: StudentExam): void {}
}
