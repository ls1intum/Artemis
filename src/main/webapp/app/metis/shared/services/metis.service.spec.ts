import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { Observable, firstValueFrom, of } from 'rxjs';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MetisCheckStatus, MetisExerciseConfigDTO, MetisGroupWindowDTO } from 'app/metis/shared/entities/metis.model';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { MetisDashboardApi } from 'app/openapi/api/metis-dashboard-api';
import { MetisExerciseConfigApi } from 'app/openapi/api/metis-exercise-config-api';
import { MetisQuestionReviewApi } from 'app/openapi/api/metis-question-review-api';
import { MetisStudentCheckApi } from 'app/openapi/api/metis-student-check-api';
import { MetisDashboard } from 'app/openapi/model/metis-dashboard';
import { MetisExerciseConfig } from 'app/openapi/model/metis-exercise-config';
import { MetisGroupWindow } from 'app/openapi/model/metis-group-window';
import { MetisQuestion } from 'app/openapi/model/metis-question';
import { MetisStudentCheck } from 'app/openapi/model/metis-student-check';

const WINDOW_START = '2026-11-03T09:00:00Z';
const WINDOW_END = '2026-11-03T09:20:00Z';
const SHOWN_AT = '2026-11-03T09:01:00Z';
const ANSWERED_AT = '2026-11-03T09:03:00Z';

/** A main question as the server sends it before any student has seen it: only the required fields. */
function serverQuestion(overrides: Partial<MetisQuestion> = {}): MetisQuestion {
    return { id: 101, checkId: 11, questionOrder: 1, questionText: 'Why can the inner loop stop early?', timeLimitSeconds: 240, regenerationCount: 0, ...overrides };
}

function serverConfig(overrides: Partial<MetisExerciseConfig> = {}): MetisExerciseConfig {
    return {
        exerciseId: 5,
        enabled: true,
        mainQuestionCount: 3,
        maxFollowUpsPerQuestion: 2,
        minSecondsPerQuestion: 60,
        maxSecondsPerQuestion: 300,
        answerLength: 3,
        complexity: 3,
        likertScalePoints: 5,
        followUpThreshold: 3,
        reviewRequired: true,
        ...overrides,
    };
}

describe('MetisService', () => {
    let service: MetisService;
    let exerciseConfigApi: { getConfig: Mock; updateConfig: Mock; getGroupWindows: Mock; updateGroupWindows: Mock; getTutorialGroups: Mock };
    let dashboardApi: { getDashboard: Mock; exportCsv: Mock; generateChecks: Mock };
    let questionReviewApi: { approveQuestion: Mock; approveAllQuestions: Mock; updateQuestionText: Mock; regenerateQuestion: Mock };
    let studentCheckApi: { getExerciseStatus: Mock; getOwnCheck: Mock; startOwnCheck: Mock; submitAnswer: Mock };

    beforeEach(() => {
        exerciseConfigApi = { getConfig: vi.fn(), updateConfig: vi.fn(), getGroupWindows: vi.fn(), updateGroupWindows: vi.fn(), getTutorialGroups: vi.fn() };
        dashboardApi = { getDashboard: vi.fn(), exportCsv: vi.fn(), generateChecks: vi.fn() };
        questionReviewApi = { approveQuestion: vi.fn(), approveAllQuestions: vi.fn(), updateQuestionText: vi.fn(), regenerateQuestion: vi.fn() };
        studentCheckApi = { getExerciseStatus: vi.fn(), getOwnCheck: vi.fn(), startOwnCheck: vi.fn(), submitAnswer: vi.fn() };
        TestBed.configureTestingModule({
            providers: [
                { provide: MetisExerciseConfigApi, useValue: exerciseConfigApi },
                { provide: MetisDashboardApi, useValue: dashboardApi },
                { provide: MetisQuestionReviewApi, useValue: questionReviewApi },
                { provide: MetisStudentCheckApi, useValue: studentCheckApi },
            ],
        });
        service = TestBed.inject(MetisService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** The single value the observable emits; every wrapper call is one request. */
    function result<T>(observable: Observable<T>): Promise<T> {
        return firstValueFrom(observable);
    }

    describe('configuration', () => {
        it('should read the configuration with its window as dayjs dates', async () => {
            exerciseConfigApi.getConfig.mockReturnValue(of(serverConfig({ windowStart: WINDOW_START, windowEnd: WINDOW_END, instructorHint: 'Focus on the loop.' })));

            const config = await result(service.config(5));

            expect(exerciseConfigApi.getConfig).toHaveBeenCalledOnce();
            expect(exerciseConfigApi.getConfig).toHaveBeenCalledWith(5);
            expect(dayjs.isDayjs(config.windowStart)).toBe(true);
            expect(config.windowStart?.isSame(dayjs(WINDOW_START))).toBe(true);
            expect(config.windowEnd?.isSame(dayjs(WINDOW_END))).toBe(true);
            expect(config).toMatchObject({ exerciseId: 5, enabled: true, mainQuestionCount: 3, likertScalePoints: 5, reviewRequired: true, instructorHint: 'Focus on the loop.' });
        });

        it('should leave an absent window and hint undefined', async () => {
            exerciseConfigApi.getConfig.mockReturnValue(of(serverConfig()));

            const config = await result(service.config(5));

            expect(config.windowStart).toBeUndefined();
            expect(config.windowEnd).toBeUndefined();
            expect(config.instructorHint).toBeUndefined();
        });

        it('should save the configuration under its exercise with ISO dates and map the stored one back', async () => {
            exerciseConfigApi.updateConfig.mockReturnValue(of(serverConfig({ windowStart: WINDOW_START, likertScalePoints: 7, followUpThreshold: 4 })));
            const config: MetisExerciseConfigDTO = {
                exerciseId: 5,
                enabled: true,
                windowStart: dayjs(WINDOW_START),
                mainQuestionCount: 3,
                maxFollowUpsPerQuestion: 2,
                minSecondsPerQuestion: 60,
                maxSecondsPerQuestion: 300,
                answerLength: 3,
                complexity: 3,
                likertScalePoints: 7,
                followUpThreshold: 4,
                reviewRequired: true,
            };

            const saved = await result(service.saveConfig(config));

            expect(exerciseConfigApi.updateConfig).toHaveBeenCalledOnce();
            const [exerciseId, body] = exerciseConfigApi.updateConfig.mock.calls[0];
            expect(exerciseId).toBe(5);
            expect(body).toEqual({
                exerciseId: 5,
                enabled: true,
                windowStart: dayjs(WINDOW_START).toJSON(),
                windowEnd: undefined,
                mainQuestionCount: 3,
                maxFollowUpsPerQuestion: 2,
                minSecondsPerQuestion: 60,
                maxSecondsPerQuestion: 300,
                answerLength: 3,
                complexity: 3,
                likertScalePoints: 7,
                followUpThreshold: 4,
                reviewRequired: true,
                instructorHint: undefined,
            });
            expect(typeof body.windowStart).toBe('string');
            expect(saved.likertScalePoints).toBe(7);
            expect(saved.windowStart?.isSame(dayjs(WINDOW_START))).toBe(true);
        });
    });

    describe('group windows and tutorial groups', () => {
        const storedWindow: MetisGroupWindow = { id: 3, exerciseId: 5, tutorialGroupId: 2, windowStart: WINDOW_START, windowEnd: WINDOW_END };

        it('should read the group windows with dayjs dates and the exercise of the path', async () => {
            exerciseConfigApi.getGroupWindows.mockReturnValue(of([storedWindow, { tutorialGroupId: 4, windowStart: WINDOW_START, windowEnd: WINDOW_END }]));

            const windows = await result(service.groupWindows(5));

            expect(exerciseConfigApi.getGroupWindows).toHaveBeenCalledWith(5);
            expect(windows).toHaveLength(2);
            expect(windows[0]).toMatchObject({ id: 3, exerciseId: 5, tutorialGroupId: 2 });
            expect(windows[0].windowStart.isSame(dayjs(WINDOW_START))).toBe(true);
            expect(windows[0].windowEnd.isSame(dayjs(WINDOW_END))).toBe(true);
            expect(windows[1].id).toBeUndefined();
            expect(windows[1].exerciseId).toBe(5);
        });

        it('should send every window with its five fields and ISO dates', async () => {
            exerciseConfigApi.updateGroupWindows.mockReturnValue(of([storedWindow]));
            const windows: MetisGroupWindowDTO[] = [
                { id: 3, exerciseId: 5, tutorialGroupId: 2, windowStart: dayjs(WINDOW_START), windowEnd: dayjs(WINDOW_END) },
                { exerciseId: 5, tutorialGroupId: 4, windowStart: dayjs(WINDOW_START), windowEnd: dayjs(WINDOW_END) },
            ];

            const saved = await result(service.saveGroupWindows(5, windows));

            expect(exerciseConfigApi.updateGroupWindows).toHaveBeenCalledOnce();
            expect(exerciseConfigApi.updateGroupWindows).toHaveBeenCalledWith(5, [
                { id: 3, exerciseId: 5, tutorialGroupId: 2, windowStart: dayjs(WINDOW_START).toJSON(), windowEnd: dayjs(WINDOW_END).toJSON() },
                { id: undefined, exerciseId: 5, tutorialGroupId: 4, windowStart: dayjs(WINDOW_START).toJSON(), windowEnd: dayjs(WINDOW_END).toJSON() },
            ]);
            expect(saved).toHaveLength(1);
            expect(saved[0].windowEnd.isSame(dayjs(WINDOW_END))).toBe(true);
        });

        it('should list the tutorial groups of the course', async () => {
            exerciseConfigApi.getTutorialGroups.mockReturnValue(of([{ id: 1, title: 'Gruppe 1' }]));

            const groups = await result(service.tutorialGroups(9));

            expect(exerciseConfigApi.getTutorialGroups).toHaveBeenCalledWith(9);
            expect(groups).toEqual([{ id: 1, title: 'Gruppe 1' }]);
        });
    });

    describe('dashboard', () => {
        it('should map the summary, the checks and the questions', async () => {
            const dashboard: MetisDashboard = {
                summary: {
                    participants: 2,
                    completed: 1,
                    inProgress: 0,
                    notStarted: 0,
                    skippedOrFailed: 1,
                    awaitingReview: 0,
                    averageScore: 3.5,
                    followUps: 1,
                    windowEnd: WINDOW_END,
                },
                rows: [
                    {
                        participationId: 1,
                        studentName: 'Anna Huber',
                        studentLogin: 'anna',
                        tutorialGroupTitle: 'Gruppe 1',
                        check: {
                            id: 11,
                            participationId: 1,
                            status: 'COMPLETED',
                            commitHash: 'a3f9c21',
                            llmModel: 'qwen3-coder:30b',
                            promptVersion: 'v1',
                            generatedAt: WINDOW_START,
                            startedAt: SHOWN_AT,
                            completedAt: ANSWERED_AT,
                        },
                        questions: [
                            serverQuestion({
                                focusFilePath: 'BubbleSort.java',
                                focusFileChangedLines: 12,
                                approvedAt: WINDOW_START,
                                shownAt: SHOWN_AT,
                                answerText: 'The last i positions are final.',
                                answeredAt: ANSWERED_AT,
                                llmScore: 3,
                                llmRationale: 'Partly correct.',
                                llmRatedAt: ANSWERED_AT,
                            }),
                            serverQuestion({ id: 102, questionOrder: 2, parentQuestionId: 101, shownAt: ANSWERED_AT }),
                        ],
                    },
                    {
                        participationId: 2,
                        studentName: 'ben',
                        studentLogin: 'ben',
                        check: { id: 22, participationId: 2, status: 'SKIPPED', failureReason: 'NO_SUBMISSION' },
                        questions: [],
                    },
                ],
            };
            dashboardApi.getDashboard.mockReturnValue(of(dashboard));

            const mapped = await result(service.dashboard(5));

            expect(dashboardApi.getDashboard).toHaveBeenCalledWith(5);
            expect(mapped.summary).toMatchObject({ participants: 2, completed: 1, skippedOrFailed: 1, averageScore: 3.5, followUps: 1 });
            expect(mapped.summary.windowEnd?.isSame(dayjs(WINDOW_END))).toBe(true);
            const [anna, ben] = mapped.rows;
            expect(anna.check.status).toBe(MetisCheckStatus.COMPLETED);
            expect(anna.check.generatedAt?.isSame(dayjs(WINDOW_START))).toBe(true);
            expect(anna.check.startedAt?.isSame(dayjs(SHOWN_AT))).toBe(true);
            expect(anna.check.completedAt?.isSame(dayjs(ANSWERED_AT))).toBe(true);
            expect(anna.check).toMatchObject({ id: 11, participationId: 1, commitHash: 'a3f9c21', llmModel: 'qwen3-coder:30b', promptVersion: 'v1' });
            expect(anna.questions.map((question) => question.id)).toEqual([101, 102]);
            const [main, followUp] = anna.questions;
            expect(main).toMatchObject({
                focusFilePath: 'BubbleSort.java',
                focusFileChangedLines: 12,
                answerText: 'The last i positions are final.',
                llmScore: 3,
                llmRationale: 'Partly correct.',
            });
            expect(main.approvedAt?.isSame(dayjs(WINDOW_START))).toBe(true);
            expect(main.answeredAt?.isSame(dayjs(ANSWERED_AT))).toBe(true);
            expect(main.llmRatedAt?.isSame(dayjs(ANSWERED_AT))).toBe(true);
            expect(followUp.parentQuestionId).toBe(101);
            expect(followUp.approvedAt).toBeUndefined();
            expect(followUp.answeredAt).toBeUndefined();
            expect(followUp.llmScore).toBeUndefined();
            expect(ben.tutorialGroupTitle).toBeUndefined();
            expect(ben.check.status).toBe(MetisCheckStatus.SKIPPED);
            expect(ben.check.failureReason).toBe('NO_SUBMISSION');
            expect(ben.check.generatedAt).toBeUndefined();
            expect(ben.questions).toEqual([]);
        });

        it.each([
            ['PENDING', MetisCheckStatus.PENDING],
            ['GENERATING', MetisCheckStatus.GENERATING],
            ['IN_REVIEW', MetisCheckStatus.IN_REVIEW],
            ['READY', MetisCheckStatus.READY],
            ['IN_PROGRESS', MetisCheckStatus.IN_PROGRESS],
            ['COMPLETED', MetisCheckStatus.COMPLETED],
            ['FAILED', MetisCheckStatus.FAILED],
            ['SKIPPED', MetisCheckStatus.SKIPPED],
        ] as const)('should map the status %s of a dashboard row', async (status, expected) => {
            dashboardApi.getDashboard.mockReturnValue(
                of({
                    summary: { participants: 1, completed: 0, inProgress: 0, notStarted: 0, skippedOrFailed: 0, awaitingReview: 0, followUps: 0 },
                    rows: [{ participationId: 1, studentName: 'anna', studentLogin: 'anna', check: { id: 1, participationId: 1, status }, questions: [] }],
                }),
            );

            const mapped = await result(service.dashboard(5));

            expect(mapped.rows[0].check.status).toBe(expected);
            expect(mapped.summary.averageScore).toBeUndefined();
            expect(mapped.summary.windowEnd).toBeUndefined();
        });

        it('should hand out the CSV file of the export', async () => {
            const file = new Blob(['participation_id\n'], { type: 'text/csv' });
            dashboardApi.exportCsv.mockReturnValue(of(new HttpResponse({ body: file, headers: new HttpHeaders({ 'Content-Type': 'text/csv' }) })));

            const blob = await result(service.exportCsv(5));

            expect(dashboardApi.exportCsv).toHaveBeenCalledWith(5);
            expect(blob).toBe(file);
        });

        it('should fail when the export response carries no file', async () => {
            dashboardApi.exportCsv.mockReturnValue(of(new HttpResponse<Blob>({ body: null })));

            await expect(result(service.exportCsv(5))).rejects.toThrow('The export response carried no file.');
        });

        it('should report the counts of generate now', async () => {
            dashboardApi.generateChecks.mockReturnValue(of({ createdChecks: 4, requeuedChecks: 2 }));

            const counts = await result(service.generateNow(5));

            expect(dashboardApi.generateChecks).toHaveBeenCalledOnce();
            expect(dashboardApi.generateChecks).toHaveBeenCalledWith(5);
            expect(counts).toEqual({ createdChecks: 4, requeuedChecks: 2 });
        });
    });

    describe('question review', () => {
        it('should approve a question of the exercise and map the stored question', async () => {
            questionReviewApi.approveQuestion.mockReturnValue(of(serverQuestion({ approvedAt: WINDOW_START })));

            const question = await result(service.approveQuestion(5, 101));

            expect(questionReviewApi.approveQuestion).toHaveBeenCalledOnce();
            expect(questionReviewApi.approveQuestion).toHaveBeenCalledWith(5, 101);
            expect(question.approvedAt?.isSame(dayjs(WINDOW_START))).toBe(true);
            expect(question.shownAt).toBeUndefined();
        });

        it('should approve every open question of the exercise without a value', async () => {
            questionReviewApi.approveAllQuestions.mockReturnValue(of(null));

            const value = await result(service.approveAllQuestions(5));

            expect(questionReviewApi.approveAllQuestions).toHaveBeenCalledWith(5);
            expect(value).toBeUndefined();
        });

        it('should send the new text as the question text', async () => {
            questionReviewApi.updateQuestionText.mockReturnValue(of(serverQuestion({ questionText: 'What does the bound guarantee?' })));

            const question = await result(service.updateQuestionText(5, 101, 'What does the bound guarantee?'));

            expect(questionReviewApi.updateQuestionText).toHaveBeenCalledOnce();
            expect(questionReviewApi.updateQuestionText).toHaveBeenCalledWith(5, 101, { questionText: 'What does the bound guarantee?' });
            expect(question.questionText).toBe('What does the bound guarantee?');
        });

        it('should regenerate a question of the exercise', async () => {
            questionReviewApi.regenerateQuestion.mockReturnValue(of(serverQuestion({ questionText: 'A new wording', regenerationCount: 1 })));

            const question = await result(service.regenerateQuestion(5, 101));

            expect(questionReviewApi.regenerateQuestion).toHaveBeenCalledWith(5, 101);
            expect(question).toMatchObject({ id: 101, questionText: 'A new wording', regenerationCount: 1 });
            expect(question.approvedAt).toBeUndefined();
        });
    });

    describe('student check', () => {
        const ownCheck: MetisStudentCheck = {
            status: 'IN_PROGRESS',
            windowStart: WINDOW_START,
            windowEnd: WINDOW_END,
            tutorialGroupTitle: 'Gruppe 2',
            mainQuestionCount: 3,
            likertScalePoints: 7,
            currentQuestion: serverQuestion({ id: 102, questionOrder: 2, parentQuestionId: 101, shownAt: ANSWERED_AT, timeLimitSeconds: 90 }),
            answeredQuestions: [serverQuestion({ shownAt: SHOWN_AT, answerText: 'Because the bound shrinks.', answeredAt: ANSWERED_AT })],
            ratingsVisible: false,
        };

        it.each([true, false])('should report whether the check runs for the exercise (%s)', async (enabled) => {
            studentCheckApi.getExerciseStatus.mockReturnValue(of({ enabled }));

            const result$ = service.isEnabledForExercise(7);

            expect(await result(result$)).toBe(enabled);
            expect(studentCheckApi.getExerciseStatus).toHaveBeenCalledWith(7);
        });

        it('should read the own check by the exercise alone and map it', async () => {
            studentCheckApi.getOwnCheck.mockReturnValue(of(ownCheck));

            const check = await result(service.studentCheck(7));

            expect(studentCheckApi.getOwnCheck).toHaveBeenCalledOnce();
            expect(studentCheckApi.getOwnCheck).toHaveBeenCalledWith(7);
            expect(check.status).toBe(MetisCheckStatus.IN_PROGRESS);
            expect(check.windowStart?.isSame(dayjs(WINDOW_START))).toBe(true);
            expect(check.windowEnd?.isSame(dayjs(WINDOW_END))).toBe(true);
            expect(check).toMatchObject({ tutorialGroupTitle: 'Gruppe 2', mainQuestionCount: 3, likertScalePoints: 7, ratingsVisible: false });
            expect(check.failureReason).toBeUndefined();
            expect(check.currentQuestion?.id).toBe(102);
            expect(check.currentQuestion?.parentQuestionId).toBe(101);
            expect(check.currentQuestion?.shownAt?.isSame(dayjs(ANSWERED_AT))).toBe(true);
            expect(check.answeredQuestions).toHaveLength(1);
            expect(check.answeredQuestions[0].answeredAt?.isSame(dayjs(ANSWERED_AT))).toBe(true);
            expect(check.answeredQuestions[0].llmScore).toBeUndefined();
        });

        it('should keep the failure reason of a skipped check and leave the absent fields undefined', async () => {
            studentCheckApi.getOwnCheck.mockReturnValue(
                of({ status: 'SKIPPED', mainQuestionCount: 3, likertScalePoints: 5, answeredQuestions: [], ratingsVisible: false, failureReason: 'CLOUD_AI_REQUIRED' }),
            );

            const check = await result(service.studentCheck(7));

            expect(check.status).toBe(MetisCheckStatus.SKIPPED);
            expect(check.failureReason).toBe('CLOUD_AI_REQUIRED');
            expect(check.windowStart).toBeUndefined();
            expect(check.windowEnd).toBeUndefined();
            expect(check.tutorialGroupTitle).toBeUndefined();
            expect(check.currentQuestion).toBeUndefined();
            expect(check.answeredQuestions).toEqual([]);
        });

        it('should start the own check of the exercise', async () => {
            studentCheckApi.startOwnCheck.mockReturnValue(of(ownCheck));

            const check = await result(service.startCheck(7));

            expect(studentCheckApi.startOwnCheck).toHaveBeenCalledOnce();
            expect(studentCheckApi.startOwnCheck).toHaveBeenCalledWith(7);
            expect(check.currentQuestion?.questionOrder).toBe(2);
        });

        it('should send the answer text to the question of the exercise', async () => {
            studentCheckApi.submitAnswer.mockReturnValue(of({ ...ownCheck, status: 'COMPLETED', currentQuestion: undefined, ratingsVisible: true }));

            const check = await result(service.submitAnswer(7, 102, 'Line 12 swaps them.'));

            expect(studentCheckApi.submitAnswer).toHaveBeenCalledOnce();
            expect(studentCheckApi.submitAnswer).toHaveBeenCalledWith(7, 102, { answerText: 'Line 12 swaps them.' });
            expect(check.status).toBe(MetisCheckStatus.COMPLETED);
            expect(check.currentQuestion).toBeUndefined();
            expect(check.ratingsVisible).toBe(true);
        });

        it('should send an empty answer as it is', async () => {
            studentCheckApi.submitAnswer.mockReturnValue(of(ownCheck));

            await result(service.submitAnswer(7, 102, ''));

            expect(studentCheckApi.submitAnswer).toHaveBeenCalledWith(7, 102, { answerText: '' });
        });
    });
});
