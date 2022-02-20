import { Exam } from 'app/entities/exam.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { of } from 'rxjs';

export class MockExamChecklistService {
    checkAtLeastOneExerciseGroup(exam: Exam) {
        return true;
    }

    checkNumberOfExerciseGroups(exam: Exam) {
        return true;
    }

    checkEachGroupContainsExercise(exam: Exam) {
        return true;
    }

    checkPointsExercisesEqual(exam: Exam) {
        return true;
    }

    checkTotalPointsMandatory(maximumPointsEqual: boolean, exam: Exam) {
        return true;
    }

    checkAtLeastOneRegisteredStudent(exam: Exam) {
        return true;
    }

    checkAllExamsGenerated(exam: Exam) {
        return true;
    }

    getExamStatistics(exam: Exam) {
        const checklist = new ExamChecklist();
        checklist.allExamExercisesAllStudentsPrepared = true;
        return of(checklist);
    }
}
