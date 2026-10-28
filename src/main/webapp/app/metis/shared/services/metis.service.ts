import { Service, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Observable, map } from 'rxjs';
import { convertDateFromClient, convertDateStringFromServer } from 'app/foundation/util/date.utils';
import {
    MetisCheckDTO,
    MetisCheckStatus,
    MetisDashboardDTO,
    MetisDashboardRowDTO,
    MetisDashboardSummaryDTO,
    MetisExerciseConfigDTO,
    MetisGenerateResultDTO,
    MetisGroupWindowDTO,
    MetisQuestionDTO,
    MetisStudentCheckDTO,
    MetisTutorialGroupDTO,
} from 'app/metis/shared/entities/metis.model';
import { MetisDashboardApi } from 'app/openapi/api/metis-dashboard-api';
import { MetisExerciseConfigApi } from 'app/openapi/api/metis-exercise-config-api';
import { MetisQuestionReviewApi } from 'app/openapi/api/metis-question-review-api';
import { MetisStudentCheckApi } from 'app/openapi/api/metis-student-check-api';
import { MetisCheck, MetisCheckStatusEnum } from 'app/openapi/model/metis-check';
import { MetisDashboard } from 'app/openapi/model/metis-dashboard';
import { MetisDashboardRow } from 'app/openapi/model/metis-dashboard-row';
import { MetisDashboardSummary } from 'app/openapi/model/metis-dashboard-summary';
import { MetisExerciseConfig } from 'app/openapi/model/metis-exercise-config';
import { MetisGenerateResult } from 'app/openapi/model/metis-generate-result';
import { MetisGroupWindow } from 'app/openapi/model/metis-group-window';
import { MetisQuestion } from 'app/openapi/model/metis-question';
import { MetisStudentCheck } from 'app/openapi/model/metis-student-check';
import { MetisTutorialGroup } from 'app/openapi/model/metis-tutorial-group';

/** The client's status for every status the server sends; both generated status unions name the same eight constants. */
const CHECK_STATUS: Record<MetisCheckStatusEnum, MetisCheckStatus> = {
    PENDING: MetisCheckStatus.PENDING,
    GENERATING: MetisCheckStatus.GENERATING,
    IN_REVIEW: MetisCheckStatus.IN_REVIEW,
    READY: MetisCheckStatus.READY,
    IN_PROGRESS: MetisCheckStatus.IN_PROGRESS,
    COMPLETED: MetisCheckStatus.COMPLETED,
    FAILED: MetisCheckStatus.FAILED,
    SKIPPED: MetisCheckStatus.SKIPPED,
};

/** A date the request requires: the forms only hold valid dates, so the conversion always yields the ISO string. */
function requiredDateFromClient(date: dayjs.Dayjs): string {
    return convertDateFromClient(date)!;
}

/** The configuration of an exercise as the forms edit it, with its window as dayjs dates. */
function toExerciseConfig(config: MetisExerciseConfig): MetisExerciseConfigDTO {
    return {
        exerciseId: config.exerciseId,
        enabled: config.enabled,
        windowStart: convertDateStringFromServer(config.windowStart),
        windowEnd: convertDateStringFromServer(config.windowEnd),
        mainQuestionCount: config.mainQuestionCount,
        maxFollowUpsPerQuestion: config.maxFollowUpsPerQuestion,
        minSecondsPerQuestion: config.minSecondsPerQuestion,
        maxSecondsPerQuestion: config.maxSecondsPerQuestion,
        answerLength: config.answerLength,
        complexity: config.complexity,
        likertScalePoints: config.likertScalePoints,
        followUpThreshold: config.followUpThreshold,
        reviewRequired: config.reviewRequired,
        instructorHint: config.instructorHint,
    };
}

/** The configuration as the server reads it, with its window as ISO strings; an empty window end stays absent. */
function toServerExerciseConfig(config: MetisExerciseConfigDTO): MetisExerciseConfig {
    return {
        exerciseId: config.exerciseId,
        enabled: config.enabled,
        windowStart: convertDateFromClient(config.windowStart),
        windowEnd: convertDateFromClient(config.windowEnd),
        mainQuestionCount: config.mainQuestionCount,
        maxFollowUpsPerQuestion: config.maxFollowUpsPerQuestion,
        minSecondsPerQuestion: config.minSecondsPerQuestion,
        maxSecondsPerQuestion: config.maxSecondsPerQuestion,
        answerLength: config.answerLength,
        complexity: config.complexity,
        likertScalePoints: config.likertScalePoints,
        followUpThreshold: config.followUpThreshold,
        reviewRequired: config.reviewRequired,
        instructorHint: config.instructorHint,
    };
}

/** A group window of the exercise in the path; both ends are required, so they become dayjs dates directly. */
function toGroupWindow(groupWindow: MetisGroupWindow, exerciseId: number): MetisGroupWindowDTO {
    return {
        id: groupWindow.id,
        exerciseId,
        tutorialGroupId: groupWindow.tutorialGroupId,
        windowStart: dayjs(groupWindow.windowStart),
        windowEnd: dayjs(groupWindow.windowEnd),
    };
}

/** A group window as the server reads it; the server keys the windows by their tutorial group and takes the exercise from the path. */
function toServerGroupWindow(groupWindow: MetisGroupWindowDTO): MetisGroupWindow {
    return {
        id: groupWindow.id,
        exerciseId: groupWindow.exerciseId,
        tutorialGroupId: groupWindow.tutorialGroupId,
        windowStart: requiredDateFromClient(groupWindow.windowStart),
        windowEnd: requiredDateFromClient(groupWindow.windowEnd),
    };
}

/** A tutorial group of the course, for the window rows. */
function toTutorialGroup(tutorialGroup: MetisTutorialGroup): MetisTutorialGroupDTO {
    return { id: tutorialGroup.id, title: tutorialGroup.title };
}

/** A question with its dates as dayjs dates; the fields the server left out (it never sends null) stay undefined. */
function toQuestion(question: MetisQuestion): MetisQuestionDTO {
    return {
        id: question.id,
        checkId: question.checkId,
        questionOrder: question.questionOrder,
        parentQuestionId: question.parentQuestionId,
        focusFilePath: question.focusFilePath,
        focusFileChangedLines: question.focusFileChangedLines,
        questionText: question.questionText,
        timeLimitSeconds: question.timeLimitSeconds,
        regenerationCount: question.regenerationCount,
        approvedAt: convertDateStringFromServer(question.approvedAt),
        shownAt: convertDateStringFromServer(question.shownAt),
        answerText: question.answerText,
        answeredAt: convertDateStringFromServer(question.answeredAt),
        llmScore: question.llmScore,
        llmRationale: question.llmRationale,
        llmRatedAt: convertDateStringFromServer(question.llmRatedAt),
    };
}

/** The check of one dashboard row with its client status and dayjs dates. */
function toCheck(check: MetisCheck): MetisCheckDTO {
    return {
        id: check.id,
        participationId: check.participationId,
        status: CHECK_STATUS[check.status],
        commitHash: check.commitHash,
        llmModel: check.llmModel,
        promptVersion: check.promptVersion,
        failureReason: check.failureReason,
        generatedAt: convertDateStringFromServer(check.generatedAt),
        startedAt: convertDateStringFromServer(check.startedAt),
        completedAt: convertDateStringFromServer(check.completedAt),
    };
}

/** One dashboard row; its questions keep the thread order the server sends. */
function toDashboardRow(row: MetisDashboardRow): MetisDashboardRowDTO {
    return {
        participationId: row.participationId,
        studentName: row.studentName,
        studentLogin: row.studentLogin,
        tutorialGroupTitle: row.tutorialGroupTitle,
        check: toCheck(row.check),
        questions: row.questions.map(toQuestion),
    };
}

/** The summary tiles; the latest window end becomes a dayjs date. */
function toDashboardSummary(summary: MetisDashboardSummary): MetisDashboardSummaryDTO {
    return {
        participants: summary.participants,
        completed: summary.completed,
        inProgress: summary.inProgress,
        notStarted: summary.notStarted,
        skippedOrFailed: summary.skippedOrFailed,
        awaitingReview: summary.awaitingReview,
        averageScore: summary.averageScore,
        followUps: summary.followUps,
        windowEnd: convertDateStringFromServer(summary.windowEnd),
    };
}

/** The dashboard of an exercise: its summary and one row per check. */
function toDashboard(dashboard: MetisDashboard): MetisDashboardDTO {
    return { summary: toDashboardSummary(dashboard.summary), rows: dashboard.rows.map(toDashboardRow) };
}

/** The student's own check with its client status, its window as dayjs dates and its questions. */
function toStudentCheck(check: MetisStudentCheck): MetisStudentCheckDTO {
    return {
        status: CHECK_STATUS[check.status],
        windowStart: convertDateStringFromServer(check.windowStart),
        windowEnd: convertDateStringFromServer(check.windowEnd),
        tutorialGroupTitle: check.tutorialGroupTitle,
        mainQuestionCount: check.mainQuestionCount,
        likertScalePoints: check.likertScalePoints,
        currentQuestion: check.currentQuestion ? toQuestion(check.currentQuestion) : undefined,
        answeredQuestions: check.answeredQuestions.map(toQuestion),
        ratingsVisible: check.ratingsVisible,
        failureReason: check.failureReason,
    };
}

/** The counts of a "generate now" request. */
function toGenerateResult(result: MetisGenerateResult): MetisGenerateResultDTO {
    return { createdChecks: result.createdChecks, requeuedChecks: result.requeuedChecks };
}

/**
 * The client of the Metis comprehension check: a thin wrapper over the four generated API services that hands the
 * components the types they are written against.
 *
 * The generated models carry dates as ISO strings and the status as the generated string unions; the wrapper turns
 * the dates into dayjs objects and back and the status into {@link MetisCheckStatus}, and builds every object anew,
 * so the components never see a generated type. It keeps no state: every call is one request.
 */
@Service()
export class MetisService {
    private readonly exerciseConfigApi = inject(MetisExerciseConfigApi);
    private readonly dashboardApi = inject(MetisDashboardApi);
    private readonly questionReviewApi = inject(MetisQuestionReviewApi);
    private readonly studentCheckApi = inject(MetisStudentCheckApi);

    /**
     * The configuration of an exercise; an exercise without one gets the defaults, switched off.
     * @param exerciseId the programming exercise
     */
    config(exerciseId: number): Observable<MetisExerciseConfigDTO> {
        return this.exerciseConfigApi.getConfig(exerciseId).pipe(map(toExerciseConfig));
    }

    /**
     * Whether the comprehension check runs for an exercise: what the exercise page needs before it shows the student tab.
     * @param exerciseId the exercise the student opened
     */
    isEnabledForExercise(exerciseId: number): Observable<boolean> {
        return this.studentCheckApi.getExerciseStatus(exerciseId).pipe(map((status) => status.enabled));
    }

    /**
     * Stores the configuration of its exercise.
     * @param config the whole configuration, as the settings form holds it
     * @returns the configuration as the server stored it
     */
    saveConfig(config: MetisExerciseConfigDTO): Observable<MetisExerciseConfigDTO> {
        return this.exerciseConfigApi.updateConfig(config.exerciseId, toServerExerciseConfig(config)).pipe(map(toExerciseConfig));
    }

    /**
     * The windows of their own that tutorial groups of the exercise have.
     * @param exerciseId the programming exercise
     */
    groupWindows(exerciseId: number): Observable<MetisGroupWindowDTO[]> {
        return this.exerciseConfigApi.getGroupWindows(exerciseId).pipe(map((groupWindows) => groupWindows.map((groupWindow) => toGroupWindow(groupWindow, exerciseId))));
    }

    /**
     * Replaces the group windows of an exercise: windows of groups that are not in the list are removed.
     * @param exerciseId the programming exercise
     * @param windows every group window the exercise should have
     * @returns the windows as the server stored them
     */
    saveGroupWindows(exerciseId: number, windows: MetisGroupWindowDTO[]): Observable<MetisGroupWindowDTO[]> {
        return this.exerciseConfigApi
            .updateGroupWindows(exerciseId, windows.map(toServerGroupWindow))
            .pipe(map((groupWindows) => groupWindows.map((groupWindow) => toGroupWindow(groupWindow, exerciseId))));
    }

    /**
     * The tutorial groups of a course, for the window rows of the settings.
     * @param courseId the course of the exercise
     */
    tutorialGroups(courseId: number): Observable<MetisTutorialGroupDTO[]> {
        return this.exerciseConfigApi.getTutorialGroups(courseId).pipe(map((tutorialGroups) => tutorialGroups.map(toTutorialGroup)));
    }

    /**
     * The summary tiles and one row per check of an exercise, each with its questions in thread order.
     * @param exerciseId the programming exercise
     */
    dashboard(exerciseId: number): Observable<MetisDashboardDTO> {
        return this.dashboardApi.getDashboard(exerciseId).pipe(map(toDashboard));
    }

    /**
     * Approves a main question; approving the last open one of a check releases the check to the student.
     * @param exerciseId the exercise of the question
     * @param questionId the main question
     * @returns the question as the server stored it
     */
    approveQuestion(exerciseId: number, questionId: number): Observable<MetisQuestionDTO> {
        return this.questionReviewApi.approveQuestion(exerciseId, questionId).pipe(map(toQuestion));
    }

    /**
     * Approves every main question of the exercise that is still open.
     * @param exerciseId the programming exercise
     */
    approveAllQuestions(exerciseId: number): Observable<void> {
        return this.questionReviewApi.approveAllQuestions(exerciseId).pipe(map(() => undefined));
    }

    /**
     * Replaces the text of a main question that no student has seen yet; an approval is kept.
     * @param exerciseId the exercise of the question
     * @param questionId the main question
     * @param text the new text
     * @returns the question as the server stored it
     */
    updateQuestionText(exerciseId: number, questionId: number, text: string): Observable<MetisQuestionDTO> {
        return this.questionReviewApi.updateQuestionText(exerciseId, questionId, { questionText: text }).pipe(map(toQuestion));
    }

    /**
     * Has the model generate a main question that no student has seen yet again; the new text withdraws an approval.
     * @param exerciseId the exercise of the question
     * @param questionId the main question
     * @returns the regenerated question
     */
    regenerateQuestion(exerciseId: number, questionId: number): Observable<MetisQuestionDTO> {
        return this.questionReviewApi.regenerateQuestion(exerciseId, questionId).pipe(map(toQuestion));
    }

    /**
     * The CSV export of an exercise, one row per question, as the server built it.
     * @param exerciseId the programming exercise
     * @returns the file; the observable fails when the response carries none
     */
    exportCsv(exerciseId: number): Observable<Blob> {
        return this.dashboardApi.exportCsv(exerciseId).pipe(
            map((response) => {
                if (!response.body) {
                    throw new Error('The export response carried no file.');
                }
                return response.body;
            }),
        );
    }

    /**
     * Creates the missing checks of an exercise at once and queues the failed ones again; the scheduling node generates them.
     * @param exerciseId the programming exercise
     * @returns how many checks were created and how many were queued again
     */
    generateNow(exerciseId: number): Observable<MetisGenerateResultDTO> {
        return this.dashboardApi.generateChecks(exerciseId).pipe(map(toGenerateResult));
    }

    /**
     * The current student's own check of an exercise; the server resolves the participation from the login.
     * @param exerciseId the exercise the student opened
     */
    studentCheck(exerciseId: number): Observable<MetisStudentCheckDTO> {
        return this.studentCheckApi.getOwnCheck(exerciseId).pipe(map(toStudentCheck));
    }

    /**
     * Starts the current student's check: the first question is shown now. Starting a check that runs already changes nothing.
     * @param exerciseId the exercise the student opened
     */
    startCheck(exerciseId: number): Observable<MetisStudentCheckDTO> {
        return this.studentCheckApi.startOwnCheck(exerciseId).pipe(map(toStudentCheck));
    }

    /**
     * Answers the current question of the student's check and returns what follows: a follow-up, the next main question or the completed check.
     * @param exerciseId the exercise the student opened
     * @param questionId the question that is open
     * @param answerText the answer; an expired timer sends whatever was typed, even nothing
     */
    submitAnswer(exerciseId: number, questionId: number, answerText: string): Observable<MetisStudentCheckDTO> {
        return this.studentCheckApi.submitAnswer(exerciseId, questionId, { answerText }).pipe(map(toStudentCheck));
    }
}
