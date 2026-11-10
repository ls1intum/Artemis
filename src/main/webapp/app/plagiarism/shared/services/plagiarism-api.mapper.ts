import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PlagiarismCase as PlagiarismCaseScoreDto } from 'app/openapi/model/plagiarism-case';
import { PlagiarismCaseDetail as PlagiarismCaseDetailDto } from 'app/openapi/model/plagiarism-case-detail';
import { PlagiarismCaseExercise as PlagiarismCaseExerciseDto } from 'app/openapi/model/plagiarism-case-exercise';
import { PlagiarismCaseInfo as PlagiarismCaseInfoDto } from 'app/openapi/model/plagiarism-case-info';
import { PlagiarismCaseOverview as PlagiarismCaseOverviewDto } from 'app/openapi/model/plagiarism-case-overview';
import { PlagiarismCasePostSummary as PlagiarismCasePostSummaryDto } from 'app/openapi/model/plagiarism-case-post-summary';
import { PlagiarismCaseUser as PlagiarismCaseUserDto } from 'app/openapi/model/plagiarism-case-user';
import { PlagiarismCaseVerdictResponse as PlagiarismCaseVerdictResponseDto } from 'app/openapi/model/plagiarism-case-verdict-response';
import { PlagiarismComparison as PlagiarismComparisonDto } from 'app/openapi/model/plagiarism-comparison';
import { PlagiarismComparisonSummary as PlagiarismComparisonSummaryDto } from 'app/openapi/model/plagiarism-comparison-summary';
import { PlagiarismMatch as PlagiarismMatchDto } from 'app/openapi/model/plagiarism-match';
import { PlagiarismSubmission as PlagiarismSubmissionDto } from 'app/openapi/model/plagiarism-submission';
import { PlagiarismSubmissionElement as PlagiarismSubmissionElementDto } from 'app/openapi/model/plagiarism-submission-element';
import { PlagiarismSubmissionForCase as PlagiarismSubmissionForCaseDto } from 'app/openapi/model/plagiarism-submission-for-case';
import {
    PlagiarismCase,
    PlagiarismCaseDTO,
    PlagiarismCaseExercise,
    PlagiarismCasePostSummary,
    PlagiarismCaseUser,
    PlagiarismCaseVerdictResponse,
} from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { PlagiarismComparison, PlagiarismComparisonSummary } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismMatch } from 'app/plagiarism/shared/entities/PlagiarismMatch';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismSubmissionElement } from 'app/plagiarism/shared/entities/PlagiarismSubmissionElement';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

/*
 * Maps the generated plagiarism transport models to the handwritten client models the plagiarism views, course scores and the exam result summary use.
 * Every field is mapped explicitly: optional values stay undefined, and dates stay the ISO strings the server sends (as before the migration).
 * Enums are converted through the client enum objects, so a value the server adds fails compilation here instead of slipping through as a string.
 */

/**
 * Converts a verdict the server sent to the client enum. The keys of {@link PlagiarismVerdict} equal its values.
 */
function toPlagiarismVerdict(verdict: keyof typeof PlagiarismVerdict): PlagiarismVerdict {
    return PlagiarismVerdict[verdict];
}

function toOptionalPlagiarismVerdict(verdict: keyof typeof PlagiarismVerdict | undefined): PlagiarismVerdict | undefined {
    return verdict === undefined ? undefined : toPlagiarismVerdict(verdict);
}

/**
 * Converts a comparison status the server sent to the client enum. The keys of {@link PlagiarismStatus} equal its values.
 */
function toOptionalPlagiarismStatus(status: keyof typeof PlagiarismStatus | undefined): PlagiarismStatus | undefined {
    return status === undefined ? undefined : PlagiarismStatus[status];
}

/**
 * Converts the exercise type the server sends (the JSON value of the Java enum) to the client enum, whose keys differ from its values.
 */
function toExerciseType(type: PlagiarismCaseExerciseDto['type']): ExerciseType {
    switch (type) {
        case 'text':
            return ExerciseType.TEXT;
        case 'programming':
            return ExerciseType.PROGRAMMING;
        case 'modeling':
            return ExerciseType.MODELING;
        case 'file-upload':
            return ExerciseType.FILE_UPLOAD;
        case 'quiz':
            return ExerciseType.QUIZ;
    }
}

function toPlagiarismCaseUser(user: PlagiarismCaseUserDto): PlagiarismCaseUser {
    return { id: user.id, login: user.login, name: user.name, visibleRegistrationNumber: user.visibleRegistrationNumber };
}

function toOptionalPlagiarismCaseUser(user: PlagiarismCaseUserDto | undefined): PlagiarismCaseUser | undefined {
    return user === undefined ? undefined : toPlagiarismCaseUser(user);
}

function toPlagiarismCasePostSummary(post: PlagiarismCasePostSummaryDto | undefined): PlagiarismCasePostSummary | undefined {
    if (post === undefined) {
        return undefined;
    }
    return { id: post.id, creationDate: post.creationDate };
}

function toPlagiarismCaseExercise(exercise: PlagiarismCaseExerciseDto): PlagiarismCaseExercise {
    return {
        id: exercise.id,
        title: exercise.title,
        shortName: exercise.shortName,
        type: toExerciseType(exercise.type),
        dueDate: exercise.dueDate,
        courseId: exercise.courseId,
        courseTitle: exercise.courseTitle,
        examId: exercise.examId,
        examTitle: exercise.examTitle,
        continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: exercise.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod,
    };
}

function toPlagiarismComparisonSummary(comparison: PlagiarismComparisonSummaryDto | undefined): PlagiarismComparisonSummary | undefined {
    if (comparison === undefined) {
        return undefined;
    }
    return { id: comparison.id, similarity: comparison.similarity, status: toOptionalPlagiarismStatus(comparison.status) };
}

function toPlagiarismSubmissionForCase(submission: PlagiarismSubmissionForCaseDto): PlagiarismSubmission {
    return {
        id: submission.id,
        submissionId: submission.submissionId,
        studentLogin: submission.studentLogin,
        size: submission.size,
        score: submission.score,
        plagiarismComparison: toPlagiarismComparisonSummary(submission.plagiarismComparison),
    };
}

/**
 * Maps a plagiarism case of the course scores to the client model the grade calculation reads.
 *
 * @param plagiarismCase the plagiarism case as sent by the server
 */
export function toPlagiarismCaseForScores(plagiarismCase: PlagiarismCaseScoreDto): PlagiarismCaseDTO {
    return { id: plagiarismCase.id, verdict: toOptionalPlagiarismVerdict(plagiarismCase.verdict), studentId: plagiarismCase.studentId };
}

/**
 * Maps a plagiarism case of the instructor overview of a course or exam to the client model.
 *
 * @param plagiarismCase the plagiarism case as sent by the server
 */
export function toPlagiarismCaseFromOverview(plagiarismCase: PlagiarismCaseOverviewDto): PlagiarismCase {
    return {
        id: plagiarismCase.id,
        exercise: toPlagiarismCaseExercise(plagiarismCase.exercise),
        student: toOptionalPlagiarismCaseUser(plagiarismCase.student),
        post: toPlagiarismCasePostSummary(plagiarismCase.post),
        verdict: toOptionalPlagiarismVerdict(plagiarismCase.verdict),
        verdictDate: plagiarismCase.verdictDate,
        verdictBy: toOptionalPlagiarismCaseUser(plagiarismCase.verdictBy),
        plagiarismSubmissionCount: plagiarismCase.plagiarismSubmissionCount,
        createdByContinuousPlagiarismControl: plagiarismCase.createdByContinuousPlagiarismControl,
        hasStudentAnswer: plagiarismCase.hasStudentAnswer,
    };
}

/**
 * Maps the detail of a plagiarism case, for the instructor or the affected student, to the client model.
 *
 * @param plagiarismCase the plagiarism case as sent by the server
 */
export function toPlagiarismCaseFromDetail(plagiarismCase: PlagiarismCaseDetailDto): PlagiarismCase {
    return {
        id: plagiarismCase.id,
        exercise: toPlagiarismCaseExercise(plagiarismCase.exercise),
        student: toOptionalPlagiarismCaseUser(plagiarismCase.student),
        post: toPlagiarismCasePostSummary(plagiarismCase.post),
        verdict: toOptionalPlagiarismVerdict(plagiarismCase.verdict),
        verdictDate: plagiarismCase.verdictDate,
        verdictBy: toOptionalPlagiarismCaseUser(plagiarismCase.verdictBy),
        verdictMessage: plagiarismCase.verdictMessage,
        verdictPointDeduction: plagiarismCase.verdictPointDeduction,
        plagiarismSubmissionCount: plagiarismCase.plagiarismSubmissionCount,
        createdByContinuousPlagiarismControl: plagiarismCase.createdByContinuousPlagiarismControl,
        plagiarismSubmissions: plagiarismCase.plagiarismSubmissions?.map(toPlagiarismSubmissionForCase),
    };
}

/**
 * Maps the saved verdict of a plagiarism case to the client model the case detail view patches the case with.
 *
 * @param response the saved verdict as sent by the server
 */
export function toPlagiarismCaseVerdictResponse(response: PlagiarismCaseVerdictResponseDto): PlagiarismCaseVerdictResponse {
    return {
        verdict: toPlagiarismVerdict(response.verdict),
        verdictMessage: response.verdictMessage,
        verdictPointDeduction: response.verdictPointDeduction,
        verdictBy: toPlagiarismCaseUser(response.verdictBy),
        verdictDate: response.verdictDate,
    };
}

/**
 * Maps the plagiarism case info of a student to the client model.
 *
 * @param info the plagiarism case info as sent by the server
 */
export function toPlagiarismCaseInfo(info: PlagiarismCaseInfoDto): PlagiarismCaseInfo {
    return { id: info.id, verdict: toOptionalPlagiarismVerdict(info.verdict), createdByContinuousPlagiarismControl: info.createdByContinuousPlagiarismControl };
}

/**
 * Maps the plagiarism case infos of a student, keyed by exercise id, to the client model.
 *
 * @param infos the plagiarism case infos as sent by the server, keyed by the exercise id
 */
export function toPlagiarismCaseInfoMap(infos: { [exerciseId: string]: PlagiarismCaseInfoDto }): { [exerciseId: number]: PlagiarismCaseInfo } {
    const result: { [exerciseId: number]: PlagiarismCaseInfo } = {};
    for (const [exerciseId, info] of Object.entries(infos)) {
        result[Number(exerciseId)] = toPlagiarismCaseInfo(info);
    }
    return result;
}

function toPlagiarismSubmissionElement(element: PlagiarismSubmissionElementDto): PlagiarismSubmissionElement {
    const result = new PlagiarismSubmissionElement();
    result.id = element.id;
    result.column = element.column;
    result.line = element.line;
    result.file = element.file;
    result.length = element.length;
    result.endLine = element.endLine;
    result.endColumn = element.endColumn;
    return result;
}

function toPlagiarismSubmission(submission: PlagiarismSubmissionDto): PlagiarismSubmission {
    return {
        id: submission.id,
        submissionId: submission.submissionId,
        studentLogin: submission.studentLogin,
        elements: submission.elements?.map(toPlagiarismSubmissionElement),
        size: submission.size,
        score: submission.score,
    };
}

function toPlagiarismMatch(match: PlagiarismMatchDto): PlagiarismMatch {
    return { startA: match.startA, startB: match.startB, length: match.length };
}

/**
 * Maps the comparison of the split view to the client model.
 *
 * Returns undefined when the server sent a comparison without one of its two submissions: the split view cannot show such a comparison, and a placeholder
 * submission would be shown as if it were real.
 *
 * @param comparison the comparison as sent by the server
 */
export function toPlagiarismComparison(comparison: PlagiarismComparisonDto): PlagiarismComparison | undefined {
    if (comparison.submissionA === undefined || comparison.submissionB === undefined) {
        return undefined;
    }
    const result = new PlagiarismComparison();
    result.id = comparison.id;
    result.submissionA = toPlagiarismSubmission(comparison.submissionA);
    result.submissionB = toPlagiarismSubmission(comparison.submissionB);
    result.matches = comparison.matches?.map(toPlagiarismMatch);
    result.similarity = comparison.similarity;
    // The status column is nullable. The split view does not read it, so a missing status leaves the field unset, as the JSON deserialization did before.
    const status = toOptionalPlagiarismStatus(comparison.status);
    if (status !== undefined) {
        result.status = status;
    }
    return result;
}
