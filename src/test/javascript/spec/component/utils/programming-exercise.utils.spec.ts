import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseUtils', () => {
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
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/123456789');
    });

    it('Should return correct commit url with different commit hash url template for student submission', () => {
        commitHashURLTemplate = 'https://example.com/{projectKey}/repositories/{repoSlug}?commit={commitHash}';
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://example.com/somekey/repositories/somekey-student42?commit=123456789');
    });

    it('Should return correct commit url for student submission and convert project key to lowercase', () => {
        projectKey = 'SOMEKEY';
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/123456789');
    });

    it('Should return correct commit url for template submission', () => {
        participantIdentifier = undefined;
        participationType = ParticipationType.TEMPLATE;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-exercise/commits/123456789');
    });

    it('Should return correct commit url for solution submission', () => {
        participantIdentifier = undefined;
        participationType = ParticipationType.SOLUTION;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-solution/commits/123456789');
    });

    it('Should return generic commits url with undefined commit hash', () => {
        commitHash = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/');
    });

    it('Should return template commit url with undefined submission type for template participation', () => {
        participationType = ParticipationType.TEMPLATE;
        submissionType = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-exercise/commits/123456789');
    });

    it('Should return solution commit url with undefined submission type for solution participation', () => {
        participationType = ParticipationType.SOLUTION;
        submissionType = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-solution/commits/123456789');
    });

    it('Should return test commit url with submission type TEST for template participation', () => {
        participationType = ParticipationType.TEMPLATE;
        submissionType = SubmissionType.TEST;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-tests/commits/123456789');
    });

    it('Should return test commit url with submission type TEST for solution participation', () => {
        participationType = ParticipationType.SOLUTION;
        submissionType = SubmissionType.TEST;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-tests/commits/123456789');
    });

    it('Should return generic commit url with undefined submission for student participation', () => {
        generateParticipationAndSubmission();
        submission = undefined;
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/');
    });

    it('Should return generic commit url with undefined submission for template participation', () => {
        participationType = ParticipationType.TEMPLATE;
        generateParticipationAndSubmission();
        submission = undefined;
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-exercise/commits/');
    });

    it('Should return generic commit url with undefined submission for solution participation', () => {
        participationType = ParticipationType.SOLUTION;
        generateParticipationAndSubmission();
        submission = undefined;
        expect(createCommitUrlResult()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-solution/commits/');
    });

    it('Should return undefined commit url with non-programming participation type', () => {
        participationType = ParticipationType.STUDENT;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.be.undefined;
    });

    it('Should return undefined commit url with undefined repository url for student participation', () => {
        repositoryUrl = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.be.undefined;
    });

    it('Should return undefined commit url with undefined participation type', () => {
        participationType = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.be.undefined;
    });

    it('Should return undefined commit url with undefined project key', () => {
        projectKey = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.be.undefined;
    });

    it('Should return undefined commit url with undefined participant identifier for student participation', () => {
        participantIdentifier = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.be.undefined;
    });

    it('Should return undefined commit url with undefined commit hash url template', () => {
        commitHashURLTemplate = undefined;
        generateParticipationAndSubmission();
        expect(createCommitUrlResult()).to.be.undefined;
    });

    it('Should return undefined commit url with undefined participation', () => {
        generateParticipationAndSubmission();
        participation = undefined;
        expect(createCommitUrlResult()).to.be.undefined;
    });
});
