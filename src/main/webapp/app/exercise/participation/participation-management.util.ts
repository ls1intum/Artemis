import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ParticipationManagementDTO } from './participation-management-dto.model';

/**
 * Builds a Result object from the flat DTO fields for use with jhi-result.
 */
export function managementDtoToResult(dto: ParticipationManagementDTO): Result | undefined {
    if (!dto.resultId) return undefined;
    const result = new Result();
    result.id = dto.resultId;
    result.score = dto.score;
    result.successful = dto.successful;
    result.completionDate = dto.completionDate;
    result.assessmentType = dto.assessmentType;
    result.testCaseCount = dto.testCaseCount;
    result.passedTestCaseCount = dto.passedTestCaseCount;
    result.codeIssueCount = dto.codeIssueCount;
    result.correctionRound = dto.correctionRoundResults?.find((roundResult) => roundResult.resultId === dto.resultId)?.correctionRound;
    return result;
}

/**
 * Builds the results the submission row works with: the newest one, which carries the score the table shows, and
 * one per correction round, which is what the assessment actions of each round act on. The newest result is often
 * one of the rounds itself, so it is not added twice.
 */
function managementDtoToResults(dto: ParticipationManagementDTO): Result[] {
    const latestResult = managementDtoToResult(dto);
    const roundResults = (dto.correctionRoundResults ?? [])
        .filter((roundResult) => roundResult.resultId !== dto.resultId)
        .map((roundResult) => {
            const result = new Result();
            result.id = roundResult.resultId;
            result.correctionRound = roundResult.correctionRound;
            result.assessmentType = roundResult.assessmentType;
            result.completionDate = roundResult.completionDate;
            result.hasComplaint = roundResult.hasComplaint;
            return result;
        });
    return latestResult ? [latestResult, ...roundResults] : roundResults;
}

/**
 * Builds the minimal submission that {@link managementDtoToParticipation} embeds. Programming exercises get a real
 * {@link ProgrammingSubmission}, which is where `buildFailed` is declared.
 */
function managementDtoToSubmission(dto: ParticipationManagementDTO, exercise?: Exercise): Submission {
    const results = managementDtoToResults(dto);
    if (exercise?.type === ExerciseType.PROGRAMMING) {
        const submission = new ProgrammingSubmission();
        submission.id = dto.submissionId;
        submission.results = results;
        submission.buildFailed = dto.buildFailed;
        return submission;
    }
    return { id: dto.submissionId, results };
}

/**
 * Builds a minimal Participation-like object from the flat DTO so that jhi-result and
 * manage-assessment-buttons can render results, assessment links and cancel buttons.
 */
export function managementDtoToParticipation(dto: ParticipationManagementDTO, exercise?: Exercise): Participation {
    return {
        id: dto.participationId,
        type: exercise?.type === ExerciseType.PROGRAMMING ? ParticipationType.PROGRAMMING : ParticipationType.STUDENT,
        exercise,
        submissionCount: dto.submissionCount,
        submissions: dto.submissionId ? [managementDtoToSubmission(dto, exercise)] : [],
    };
}
