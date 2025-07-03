import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import {
    createBuildPlanUrl,
    hasDueDatePassed,
    isProgrammingExerciseParticipation,
    isProgrammingExerciseStudentParticipation,
    isResultPreliminary,
} from 'app/programming/shared/utils/programming-exercise.utils';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import dayjs from 'dayjs/esm';

describe('ProgrammingExerciseUtils', () => {
    it('createBuildPlanUrl fills in buildPlanId and projectKey', () => {
        const template = '/job/{projectKey}/job/{buildPlanId}';
        const buildPlanId = 'BPID';
        const projectKey = 'PK';

        const generatedUrl = createBuildPlanUrl(template, projectKey, buildPlanId);

        const expectedUrl = '/job/PK/job/BPID';
        expect(generatedUrl).toBe(expectedUrl);
    });

    describe('isProgrammingExerciseStudentParticipation', () => {
        it('returns true for a programming exercise participation', () => {
            const participation = new ProgrammingExerciseStudentParticipation();
            expect(isProgrammingExerciseStudentParticipation(participation)).toBeTrue();
        });

        it('returns false for another participation', () => {
            const participation = new TemplateProgrammingExerciseParticipation();
            expect(isProgrammingExerciseStudentParticipation(participation)).toBeFalse();
        });
    });

    describe('isProgrammingExerciseParticipation', () => {
        it('returns false for an undefined participation', () => {
            const participation = undefined;
            expect(isProgrammingExerciseParticipation(participation)).toBeFalse();
        });

        it('returns true for a student programming exercise participation', () => {
            const participation = new ProgrammingExerciseStudentParticipation();
            expect(isProgrammingExerciseParticipation(participation)).toBeTrue();
        });

        it('returns true for a template programming exercise participation', () => {
            const participation = new TemplateProgrammingExerciseParticipation();
            expect(isProgrammingExerciseParticipation(participation)).toBeTrue();
        });

        it('returns true for a solution programming exercise participation', () => {
            const participation = new SolutionProgrammingExerciseParticipation();
            expect(isProgrammingExerciseParticipation(participation)).toBeTrue();
        });

        it('returns false for a normal student participation', () => {
            const participation = new StudentParticipation();
            expect(isProgrammingExerciseParticipation(participation)).toBeFalse();
        });
    });

    describe('hasDueDatePassed', () => {
        let exercise: ProgrammingExercise;

        beforeEach(() => {
            exercise = new ProgrammingExercise(undefined, undefined);
        });

        it('returns false if no due date is set', () => {
            expect(hasDueDatePassed(exercise)).toBeFalse();
        });

        it('buildAndTestDate takes precedence over normal exercise due date', () => {
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().add(5, 'hours');
            exercise.dueDate = dayjs().subtract(5, 'hours');
            expect(hasDueDatePassed(exercise)).toBeFalse();
        });

        it('returns true on date in the past', () => {
            exercise.dueDate = dayjs().subtract(1, 'hour');
            expect(hasDueDatePassed(exercise)).toBeTrue();
        });

        it('returns false on date in the future', () => {
            exercise.dueDate = dayjs().add(1, 'hour');
            expect(hasDueDatePassed(exercise)).toBeFalse();
        });
    });

    describe('isResultPreliminary', () => {
        let result: Result;
        let exercise: ProgrammingExercise;
        let participation: ProgrammingExerciseStudentParticipation;

        beforeEach(() => {
            result = new Result();
            exercise = new ProgrammingExercise(undefined, undefined);
            participation = new ProgrammingExerciseStudentParticipation();
            exercise.assessmentType = AssessmentType.AUTOMATIC;
        });

        it('returns false on undefined exercise', () => {
            expect(isResultPreliminary(result, participation, undefined)).toBeFalse();
        });

        it('return true if the result completion date is not set', () => {
            expect(isResultPreliminary(result, participation, exercise)).toBeTrue();
        });

        it('return true on invalid date', () => {
            result.completionDate = dayjs('Invalid date');
            expect(isResultPreliminary(result, participation, exercise)).toBeTrue();
        });

        describe('manual assessment set for the exercise', () => {
            beforeEach(() => {
                exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                result.completionDate = dayjs();
                participation = new ProgrammingExerciseStudentParticipation();
            });

            it('return true if the assessment due date is set and in the future', () => {
                exercise.assessmentDueDate = dayjs().add(5, 'hours');
                expect(isResultPreliminary(result, participation, exercise)).toBeTrue();
            });

            it('return false if the assessment due date is set and in the past', () => {
                exercise.assessmentDueDate = dayjs().subtract(5, 'hours');
                expect(isResultPreliminary(result, participation, exercise)).toBeFalse();
            });

            it('return true if the assessment due date is not set and the latest result is an automatic assessment', () => {
                result.assessmentType = AssessmentType.AUTOMATIC;
                expect(isResultPreliminary(result, participation, exercise)).toBeTrue();
            });

            it('return false if the assessment due date is not set and the latest result is not an automatic assessment', () => {
                result.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                expect(isResultPreliminary(result, participation, exercise)).toBeFalse();
            });
        });

        it('return true if buildAndTest date is set and in the future', () => {
            result.completionDate = dayjs();
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().add(5, 'hours');
            expect(isResultPreliminary(result, participation, exercise)).toBeTrue();
        });

        it('return false if buildAndTest date is set and in the past', () => {
            result.completionDate = dayjs();
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().subtract(5, 'hours');
            expect(isResultPreliminary(result, participation, exercise)).toBeFalse();
        });

        it('return false if completion date is valid and buildAndTest date is not set', () => {
            result.completionDate = dayjs();
            expect(isResultPreliminary(result, participation, exercise)).toBeFalse();
        });
    });
});
