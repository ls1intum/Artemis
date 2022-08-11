import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import {
    createBuildPlanUrl,
    createCommitUrl,
    hasDeadlinePassed,
    isLegacyResult,
    isProgrammingExerciseParticipation,
    isProgrammingExerciseStudentParticipation,
    isResultPreliminary,
} from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import dayjs from 'dayjs/esm';

describe('ProgrammingExerciseUtils URL utils', () => {
    let commitHashURLTemplate: string | undefined;

    let exercise: ProgrammingExercise | undefined;
    let participation: Participation | undefined;
    let submission: ProgrammingSubmission | undefined;

    let submissionType: SubmissionType | undefined;
    let participationType: ParticipationType | undefined;
    let commitHash: string | undefined;
    let projectKey: string | undefined;
    let participantIdentifier: string | undefined;
    let repositoryUrl: string | undefined;

    function generateParticipationAndSubmission() {
        exercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            projectKey,
        } as ProgrammingExercise;
        // there is no ProgrammingParticipation-like supertype here
        participation = {
            id: 2,
            type: participationType,
            participantIdentifier,
            repositoryUrl,
            programmingExercise: exercise,
        } as Participation & { programmingExercise: ProgrammingExercise; repositoryUrl: string | undefined; participantIdentifier: string | undefined };
        submission = {
            id: 3,
            submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
            type: submissionType,
            commitHash,
            participation,
        } as ProgrammingSubmission;
    }

    function createCommitUrlResult() {
        return createCommitUrl(commitHashURLTemplate, projectKey, participation, submission);
    }

    beforeEach(() => {
        commitHashURLTemplate = 'https://bitbucket.ase.in.tum.de/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}';
        submissionType = SubmissionType.MANUAL;
        participationType = ParticipationType.PROGRAMMING;
        commitHash = '123456789';
        projectKey = 'somekey';
        participantIdentifier = 'student42';
        repositoryUrl = 'https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42';
    });

    it('Should return correct commit url for student submission', () => {
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/123456789');
    });

    it('Should return correct commit url with different commit hash url template for student submission', () => {
        commitHashURLTemplate = 'https://example.com/{projectKey}/repositories/{repoSlug}?commit={commitHash}';
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://example.com/somekey/repositories/somekey-student42?commit=123456789');
    });

    it('Should return correct commit url for student submission and convert project key to lowercase', () => {
        projectKey = 'SOMEKEY';
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/123456789');
    });

    it('Should return correct commit url for template submission', () => {
        participantIdentifier = undefined;
        participationType = ParticipationType.TEMPLATE;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-exercise/commits/123456789');
    });

    it('Should return correct commit url for solution submission', () => {
        participantIdentifier = undefined;
        participationType = ParticipationType.SOLUTION;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-solution/commits/123456789');
    });

    it('Should return generic commits url with undefined commit hash', () => {
        commitHash = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/');
    });

    it('Should return template commit url with undefined submission type for template participation', () => {
        participationType = ParticipationType.TEMPLATE;
        submissionType = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-exercise/commits/123456789');
    });

    it('Should return solution commit url with undefined submission type for solution participation', () => {
        participationType = ParticipationType.SOLUTION;
        submissionType = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-solution/commits/123456789');
    });

    it('Should return test commit url with submission type TEST for template participation', () => {
        participationType = ParticipationType.TEMPLATE;
        submissionType = SubmissionType.TEST;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-tests/commits/123456789');
    });

    it('Should return test commit url with submission type TEST for solution participation', () => {
        participationType = ParticipationType.SOLUTION;
        submissionType = SubmissionType.TEST;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-tests/commits/123456789');
    });

    it('Should return generic commit url with undefined submission for student participation', () => {
        generateParticipationAndSubmission();
        submission = undefined;
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/');
    });

    it('Should return generic commit url with undefined submission for template participation', () => {
        participationType = ParticipationType.TEMPLATE;
        generateParticipationAndSubmission();
        submission = undefined;
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-exercise/commits/');
    });

    it('Should return generic commit url with undefined submission for solution participation', () => {
        participationType = ParticipationType.SOLUTION;
        generateParticipationAndSubmission();
        submission = undefined;
        expect(createCommitUrlResult()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-solution/commits/');
    });

    it('Should return undefined commit url with non-programming participation type', () => {
        participationType = ParticipationType.STUDENT;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBeUndefined();
    });

    it('Should return undefined commit url with undefined repository url for student participation', () => {
        repositoryUrl = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBeUndefined();
    });

    it('Should return undefined commit url with undefined participation type', () => {
        participationType = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBeUndefined();
    });

    it('Should return undefined commit url with undefined project key', () => {
        projectKey = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBeUndefined();
    });

    it('Should return undefined commit url with undefined participant identifier for student participation', () => {
        participantIdentifier = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBeUndefined();
    });

    it('Should return undefined commit url with undefined commit hash url template', () => {
        commitHashURLTemplate = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).toBeUndefined();
    });

    it('Should return undefined commit url with undefined participation', () => {
        generateParticipationAndSubmission();
        participation = undefined;
        expect(createCommitUrlResult()).toBeUndefined();
    });
});

describe('ProgrammingExerciseUtils', () => {
    describe('isLegacy', () => {
        const legacyDate = dayjs('2019-05-10T22:12:27Z');

        it('returns false when the completion date is not set', () => {
            const result = new Result();
            expect(isLegacyResult(result)).toBeFalse();
        });

        it('returns true on legacy result', () => {
            const result = new Result();
            result.completionDate = legacyDate;
            expect(isLegacyResult(result)).toBeTrue();
        });

        it('returns false on non legacy result', () => {
            const result = new Result();
            result.completionDate = legacyDate.add(1, 'second');
            expect(isLegacyResult(result)).toBeFalse();
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

    describe('hasDeadlinePassed', () => {
        let exercise: ProgrammingExercise;

        beforeEach(() => {
            exercise = new ProgrammingExercise(undefined, undefined);
        });

        it('returns false if no due date is set', () => {
            expect(hasDeadlinePassed(exercise)).toBeFalse();
        });

        it('buildAndTestDate takes precedence over normal exercise due date', () => {
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().add(5, 'hours');
            exercise.dueDate = dayjs().subtract(5, 'hours');
            expect(hasDeadlinePassed(exercise)).toBeFalse();
        });

        it('returns true on date in the past', () => {
            exercise.dueDate = dayjs().subtract(1, 'hour');
            expect(hasDeadlinePassed(exercise)).toBeTrue();
        });

        it('returns false on date in the future', () => {
            exercise.dueDate = dayjs().add(1, 'hour');
            expect(hasDeadlinePassed(exercise)).toBeFalse();
        });
    });

    describe('isResultPreliminary', () => {
        let result: Result;
        let exercise: ProgrammingExercise;

        beforeEach(() => {
            result = new Result();
            exercise = new ProgrammingExercise(undefined, undefined);
            exercise.assessmentType = AssessmentType.AUTOMATIC;
        });

        it('returns false on undefined exercise', () => {
            expect(isResultPreliminary(result, undefined)).toBeFalse();
        });

        it('return true if the result completion date is not set', () => {
            expect(isResultPreliminary(result, exercise)).toBeTrue();
        });

        it('return true on invalid date', () => {
            result.completionDate = dayjs('Invalid date');
            expect(isResultPreliminary(result, exercise)).toBeTrue();
        });

        describe('manual assessment set for the exercise', () => {
            beforeEach(() => {
                exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                result.completionDate = dayjs();
            });

            it('return true if the assessment due date is set and in the future', () => {
                exercise.assessmentDueDate = dayjs().add(5, 'hours');
                expect(isResultPreliminary(result, exercise)).toBeTrue();
            });

            it('return false if the assessment due date is set and in the past', () => {
                exercise.assessmentDueDate = dayjs().subtract(5, 'hours');
                expect(isResultPreliminary(result, exercise)).toBeFalse();
            });

            it('return true if the assessment due date is not set and the latest result is an automatic assessment', () => {
                result.assessmentType = AssessmentType.AUTOMATIC;
                expect(isResultPreliminary(result, exercise)).toBeTrue();
            });

            it('return false if the assessment due date is not set and the latest result is not an automatic assessment', () => {
                result.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                expect(isResultPreliminary(result, exercise)).toBeFalse();
            });
        });

        it('return true if buildAndTest date is set and in the future', () => {
            result.completionDate = dayjs();
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().add(5, 'hours');
            expect(isResultPreliminary(result, exercise)).toBeTrue();
        });

        it('return false if buildAndTest date is set and in the past', () => {
            result.completionDate = dayjs();
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs().subtract(5, 'hours');
            expect(isResultPreliminary(result, exercise)).toBeFalse();
        });

        it('return false if completion date is valid and buildAndTest date is not set', () => {
            result.completionDate = dayjs();
            expect(isResultPreliminary(result, exercise)).toBeFalse();
        });
    });
});
