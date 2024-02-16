import { prepareFeedbackComponentParameters } from 'app/exercises/shared/feedback/feedback.utils';
import { ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TestBed } from '@angular/core/testing';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

describe('FeedbackUtils', () => {
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

            expect(preparedParameters.showScoreChart).toBeTrue();
            expect(preparedParameters.messageKey).toBe('artemisApp.result.notLatestSubmission');
            expect(preparedParameters.showMissingAutomaticFeedbackInformation).toBeFalse();
        });

        it('should determine automatic feedback information if latestDueDate is passed as undefined', () => {
            const exerciseServiceLatestDueDate = dayjs().add(4, 'hours');
            const getLatestDueDateSpy = jest.spyOn(exerciseService, 'getLatestDueDate').mockReturnValue(of(exerciseServiceLatestDueDate));

            const preparedParameters = prepareFeedbackComponentParameters(exercise, result, participation, ResultTemplateStatus.HAS_RESULT, undefined, exerciseService);

            expect(getLatestDueDateSpy).toHaveBeenCalledOnce();
            expect(preparedParameters.showScoreChart).toBeTrue();
            expect(preparedParameters.messageKey).not.toBeTruthy();
            expect(preparedParameters.latestDueDate).toEqual(exerciseServiceLatestDueDate);
            expect(preparedParameters.showMissingAutomaticFeedbackInformation).toBeTrue();
        });
    });
});
