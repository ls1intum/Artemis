import { expect, vi } from 'vitest';
import { prepareFeedbackComponentParameters } from 'app/exercise/feedback/feedback.utils';
import { ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExerciseService } from 'app/exercise/services/exercise.service';

describe('FeedbackUtils', () => {
    setupTestBed({ zoneless: true });
    let exerciseService: ExerciseService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(ExerciseService)],
        })
            .compileComponents()
            .then(() => {
                exerciseService = TestBed.inject(ExerciseService);
            });
    });

    describe('prepareFeedbackComponentParameters', () => {
        const exercise = { id: 42, type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(4, 'hours') } as ProgrammingExercise;
        const result = { assessmentType: AssessmentType.AUTOMATIC };
        const participation = {};
        const templateStatus = ResultTemplateStatus.MISSING;
        const latestDueDate = dayjs().subtract(4, 'hours');

        it('should determine automatic feedback information with latestDueDate being passed', () => {
            const preparedParameters = prepareFeedbackComponentParameters(exercise, result, participation, templateStatus, latestDueDate, exerciseService);

            expect(preparedParameters.showScoreChart).toBe(true);
            expect(preparedParameters.messageKey).toBe('artemisApp.result.notLatestSubmission');
            expect(preparedParameters.showMissingAutomaticFeedbackInformation).toBe(false);
        });

        it('should determine automatic feedback information if latestDueDate is passed as undefined', () => {
            const exerciseServiceLatestDueDate = dayjs().add(4, 'hours');
            const getLatestDueDateSpy = vi.spyOn(exerciseService, 'getLatestDueDate').mockReturnValue(of(exerciseServiceLatestDueDate));

            const preparedParameters = prepareFeedbackComponentParameters(exercise, result, participation, ResultTemplateStatus.HAS_RESULT, undefined, exerciseService);

            expect(getLatestDueDateSpy).toHaveBeenCalledOnce();
            expect(preparedParameters.showScoreChart).toBe(true);
            expect(preparedParameters.messageKey).not.toBeTruthy();
            expect(preparedParameters.latestDueDate).toEqual(exerciseServiceLatestDueDate);
            expect(preparedParameters.showMissingAutomaticFeedbackInformation).toBe(true);
        });
    });
});
