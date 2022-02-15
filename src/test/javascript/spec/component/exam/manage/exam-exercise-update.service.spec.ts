import { ArtemisTestModule } from '../../../test.module';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { TestBed } from '@angular/core/testing';

describe('Exam Exercise Update Service Tests', () => {
    let service: ExamExerciseUpdateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ExamExerciseUpdateService],
            imports: [ArtemisTestModule],
        });

        service = TestBed.inject(ExamExerciseUpdateService);
    });

    it('should forward navigation call to observable', () => {
        let id = -1;
        service.currentExerciseIdForNavigation.subscribe((nextId) => (id = nextId));
        service.navigateToExamExercise(1);
        expect(id).toBe(1);
    });

    it('should forward updateLiveExam calls to observable', () => {
        let exerciseIdAndProblemStatement = undefined;
        service.currentExerciseIdAndProblemStatement.subscribe((nextObject) => (exerciseIdAndProblemStatement = nextObject));
        service.updateLiveExamExercise(2, 'problem-test');
        expect(exerciseIdAndProblemStatement).toEqual({ exerciseId: 2, problemStatement: 'problem-test' });
    });
});
