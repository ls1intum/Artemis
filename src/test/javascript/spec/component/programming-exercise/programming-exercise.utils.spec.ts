import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import {
    isLegacyResult,
    createBuildPlanUrl,
    createCommitUrl,
    isProgrammingExerciseStudentParticipation,
    hasDeadlinePassed,
    isProgrammingExerciseParticipation,
} from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs';

describe('Programming Exercise Utils', () => {
    describe('isLegacy', () => {
        const legacyDate = dayjs('2019-05-10T22:12:27Z');

        it('returns false when the completion date is not set', () => {
            const result = new Result();

            const isLegacy = isLegacyResult(result);

            expect(isLegacy).toBe(false);
        });

        it('returns true on legacy result', () => {
            const result = new Result();
            result.completionDate = legacyDate;

            const isLegacy = isLegacyResult(result);

            expect(isLegacy).toBe(true);
        });

        it('returns false on non legacy result', () => {
            const result = new Result();
            result.completionDate = legacyDate.add(1, 'second');

            const isLegacy = isLegacyResult(result);

            expect(isLegacy).toBe(false);
        });
    });

    it('createBuildPlanUrl fills in buildPlanId and projectKey', () => {
        const template = '/job/{projectKey}/job/{buildPlanId}';
        const buildPlanId = 'BPID';
        const projectKey = 'PK';

        const generatedUrl = createBuildPlanUrl(template, projectKey, buildPlanId);

        const expectedUrl = '/job/PK/job/BPID';
        expect(generatedUrl).toBe(expectedUrl);
    });

    describe('createCommitUrl', () => {
        const template = '/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}';
        const projectKey = 'PK';
        const commitHash = 'a3dcb4d229de6fde0db5686dee47145d';

        it('creates a commit url for a student submission', () => {
            const participantID = 'PID';
            const participation = new ProgrammingExerciseStudentParticipation();
            participation.repositoryUrl = 'repositoryUrl';
            participation.participantIdentifier = participantID;
            const submission = new ProgrammingSubmission();
            submission.commitHash = commitHash;

            const url = createCommitUrl(template, projectKey, participation, submission);

            const expectedUrl = '/projects/pk/repos/pk-' + participantID + '/commits/' + commitHash;
            expect(url).toBe(expectedUrl);
        });

        it('creates a commit url for a template submission', () => {
            const participation = new TemplateProgrammingExerciseParticipation();
            const submission = new ProgrammingSubmission();
            submission.type = SubmissionType.MANUAL;
            submission.commitHash = commitHash;

            const url = createCommitUrl(template, projectKey, participation, submission);

            const expectedUrl = '/projects/pk/repos/pk-exercise/commits/' + commitHash;
            expect(url).toBe(expectedUrl);
        });

        it('creates a commit url for a template test submission', () => {
            const participation = new TemplateProgrammingExerciseParticipation();
            const submission = new ProgrammingSubmission();
            submission.type = SubmissionType.TEST;
            submission.commitHash = commitHash;

            const url = createCommitUrl(template, projectKey, participation, submission);

            const expectedUrl = '/projects/pk/repos/pk-tests/commits/' + commitHash;
            expect(url).toBe(expectedUrl);
        });

        it('creates a commit url for a solution submission', () => {
            const participation = new SolutionProgrammingExerciseParticipation();
            const submission = new ProgrammingSubmission();
            submission.type = SubmissionType.MANUAL;
            submission.commitHash = commitHash;

            const url = createCommitUrl(template, projectKey, participation, submission);

            const expectedUrl = '/projects/pk/repos/pk-solution/commits/' + commitHash;
            expect(url).toBe(expectedUrl);
        });

        it('creates a commit url for a solution test submission', () => {
            const participation = new SolutionProgrammingExerciseParticipation();
            const submission = new ProgrammingSubmission();
            submission.type = SubmissionType.TEST;
            submission.commitHash = commitHash;

            const url = createCommitUrl(template, projectKey, participation, submission);

            const expectedUrl = '/projects/pk/repos/pk-tests/commits/' + commitHash;
            expect(url).toBe(expectedUrl);
        });

        it('creates a commit url without a commit hash', () => {
            const participation = new SolutionProgrammingExerciseParticipation();
            const submission = new ProgrammingSubmission();
            submission.type = SubmissionType.MANUAL;

            const url = createCommitUrl(template, projectKey, participation, submission);

            const expectedUrl = '/projects/pk/repos/pk-solution/commits/';
            expect(url).toBe(expectedUrl);
        });
    });

    describe('isProgrammingExerciseStudentParticipation', () => {
        it('returns true for a programming exercise participation', () => {
            const participation = new ProgrammingExerciseStudentParticipation();

            const isProgrammingStudentParticipation = isProgrammingExerciseStudentParticipation(participation);

            expect(isProgrammingStudentParticipation).toBe(true);
        });

        it('returns false for another participation', () => {
            const participation = new TemplateProgrammingExerciseParticipation();

            const isProgrammingStudentParticipation = isProgrammingExerciseStudentParticipation(participation);

            expect(isProgrammingStudentParticipation).toBe(false);
        });
    });

    describe('isProgrammingExerciseParticipation', () => {
        it('returns false for an undefined participation', () => {
            const participation = undefined;

            const isProgrammingParticipation = isProgrammingExerciseParticipation(participation);

            expect(isProgrammingParticipation).toBe(false);
        });

        it('returns true for a student programming exercise participation', () => {
            const participation = new ProgrammingExerciseStudentParticipation();

            const isProgrammingStudentParticipation = isProgrammingExerciseParticipation(participation);

            expect(isProgrammingStudentParticipation).toBe(true);
        });

        it('returns true for a template programming exercise participation', () => {
            const participation = new TemplateProgrammingExerciseParticipation();

            const isProgrammingStudentParticipation = isProgrammingExerciseParticipation(participation);

            expect(isProgrammingStudentParticipation).toBe(true);
        });

        it('returns true for a solution programming exercise participation', () => {
            const participation = new SolutionProgrammingExerciseParticipation();

            const isProgrammingStudentParticipation = isProgrammingExerciseParticipation(participation);

            expect(isProgrammingStudentParticipation).toBe(true);
        });

        it('returns false for a normal student participation', () => {
            const participation = new StudentParticipation();

            const isProgrammingStudentParticipation = isProgrammingExerciseParticipation(participation);

            expect(isProgrammingStudentParticipation).toBe(false);
        });
    });

    describe('hasDeadlinePassed', () => {
        let exercise: ProgrammingExercise;

        beforeEach(() => {
            exercise = new ProgrammingExercise(undefined, undefined);
        });

        it('returns false if no due date is set', () => {
            const deadlinePassed = hasDeadlinePassed(exercise);

            expect(deadlinePassed).toBe(false);
        });

        it('buildAndTestDate takes precedence over normal exercise due date', () => {
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().add(5, 'hours');
            exercise.dueDate = dayjs().subtract(5, 'hours');

            const deadlinePassed = hasDeadlinePassed(exercise);

            expect(deadlinePassed).toBe(false);
        });

        it('returns true on date in the past', () => {
            exercise.dueDate = dayjs().subtract(1, 'hour');

            const deadlinePassed = hasDeadlinePassed(exercise);

            expect(deadlinePassed).toBe(true);
        });

        it('returns false on date in the future', () => {
            exercise.dueDate = dayjs().add(1, 'hour');

            const deadlinePassed = hasDeadlinePassed(exercise);

            expect(deadlinePassed).toBe(false);
        });
    });
});
