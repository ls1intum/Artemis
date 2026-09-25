import dayjs from 'dayjs/esm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseApi } from 'app/openapi/api/quiz-exercise-api';
import { QuizExerciseBatchApi } from 'app/openapi/api/quiz-exercise-batch-api';
import type { QuizBatch as GeneratedQuizBatch } from 'app/openapi/model/quiz-batch';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('QuizExercise Lifecycle Buttons Component', () => {
    let comp: QuizExerciseLifecycleButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseLifecycleButtonsComponent>;
    let quizExerciseApi: QuizExerciseApi;
    let quizExerciseBatchApi: QuizExerciseBatchApi;
    let alertService: AlertService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    quizExercise.quizQuestions = [];
    const quizBatch = new QuizBatch();
    quizBatch.id = 567;
    // The generated models carry ISO strings where the class carries dayjs, so the wire fixture is its own object.
    const generatedQuizBatch: GeneratedQuizBatch = { id: 567 };
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        })
            .overrideTemplate(QuizExerciseLifecycleButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseLifecycleButtonsComponent);
        comp = fixture.componentInstance;
        quizExerciseApi = TestBed.inject(QuizExerciseApi);
        quizExerciseBatchApi = TestBed.inject(QuizExerciseBatchApi);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should start quiz', () => {
        vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({}));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.startQuiz();
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledWith(456, 'start-now');
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledOnce();
    });

    it('should not start quiz on error', () => {
        vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        vi.spyOn(alertService, 'error');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.startQuiz();
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledWith(456, 'start-now');
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    it('should end quiz', () => {
        vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({}));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.endQuiz();
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledWith(456, 'end-now');
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledOnce();
    });

    it('should add quiz batch', () => {
        vi.spyOn(quizExerciseBatchApi, 'addBatch').mockReturnValue(of(generatedQuizBatch));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.addBatch();
        expect(quizExerciseBatchApi.addBatch).toHaveBeenCalledWith(456);
        expect(quizExerciseBatchApi.addBatch).toHaveBeenCalledOnce();
    });

    it('should start quiz batch', () => {
        vi.spyOn(quizExerciseBatchApi, 'startBatch_').mockReturnValue(of(generatedQuizBatch));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.startBatch(567);
        expect(quizExerciseBatchApi.startBatch_).toHaveBeenCalledWith(567);
        expect(quizExerciseBatchApi.startBatch_).toHaveBeenCalledOnce();
    });

    it('should make quiz visible', () => {
        vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({}));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.showQuiz();
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledWith(456, 'set-visible');
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledOnce();
    });

    it('should not make quiz visible on error', () => {
        vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        vi.spyOn(alertService, 'error');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.showQuiz();
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledWith(456, 'set-visible');
        expect(quizExerciseApi.performActionForQuizExercise).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
    });

    /** Reads the protected show* computeds. */
    function flags(): {
        showStartButton: () => boolean;
        showEndButton: () => boolean;
        showSetVisibleButton: () => boolean;
        showBatchMenu: () => boolean;
        isInVariantGroup: () => boolean;
    } {
        return comp as never;
    }

    function buildQuiz(overrides: Partial<QuizExercise>): QuizExercise {
        return { ...quizExercise, ...overrides } as QuizExercise;
    }

    describe('button visibility', () => {
        it('shows the start button only for a not-yet-started synchronized quiz with editor rights', () => {
            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.VISIBLE, quizMode: QuizMode.SYNCHRONIZED, isAtLeastEditor: true, quizStarted: false }));
            expect(flags().showStartButton()).toBe(true);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.VISIBLE, quizMode: QuizMode.SYNCHRONIZED, isAtLeastEditor: true, quizStarted: true }));
            expect(flags().showStartButton()).toBe(false);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.VISIBLE, quizMode: QuizMode.BATCHED, isAtLeastEditor: true, quizStarted: false }));
            expect(flags().showStartButton()).toBe(false);
        });

        it('shows the end button only for running non-synchronized quizzes with instructor rights', () => {
            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.ACTIVE, quizMode: QuizMode.INDIVIDUAL, isAtLeastInstructor: true, quizEnded: false }));
            expect(flags().showEndButton()).toBe(true);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.ACTIVE, quizMode: QuizMode.SYNCHRONIZED, isAtLeastInstructor: true, quizEnded: false }));
            expect(flags().showEndButton()).toBe(false);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.ACTIVE, quizMode: QuizMode.INDIVIDUAL, isAtLeastInstructor: true, quizEnded: true }));
            expect(flags().showEndButton()).toBe(false);
        });

        it('shows the set-visible button only for invisible quizzes with editor rights', () => {
            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.INVISIBLE, isAtLeastEditor: true, visibleToStudents: false }));
            expect(flags().showSetVisibleButton()).toBe(true);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.INVISIBLE, isAtLeastEditor: false, visibleToStudents: false }));
            expect(flags().showSetVisibleButton()).toBe(false);
        });

        it('shows the batch menu only for visible or active batched quizzes', () => {
            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.VISIBLE, quizMode: QuizMode.BATCHED }));
            expect(flags().showBatchMenu()).toBe(true);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.INVISIBLE, quizMode: QuizMode.BATCHED }));
            expect(flags().showBatchMenu()).toBe(false);

            fixture.componentRef.setInput('quizExercise', buildQuiz({ status: QuizStatus.VISIBLE, quizMode: QuizMode.SYNCHRONIZED }));
            expect(flags().showBatchMenu()).toBe(false);
        });

        it('detects variant group membership', () => {
            fixture.componentRef.setInput('quizExercise', buildQuiz({ exerciseVariantGroup: { id: 3 } }));
            expect(flags().isInVariantGroup()).toBe(true);

            fixture.componentRef.setInput('quizExercise', buildQuiz({}));
            expect(flags().isInVariantGroup()).toBe(false);
        });
    });

    describe('optimistic state updates', () => {
        it('converts the dates the server returns into dayjs before emitting', () => {
            vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({ releaseDate: '2026-05-01T10:00:00Z', dueDate: '2026-05-01T11:00:00Z' }));
            fixture.componentRef.setInput('quizExercise', buildQuiz({}));
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));

            comp.showQuiz();

            expect(dayjs.isDayjs(emitted[0].releaseDate)).toBe(true);
            expect(emitted[0].releaseDate!.toISOString()).toBe('2026-05-01T10:00:00.000Z');
            expect(emitted[0].dueDate!.toISOString()).toBe('2026-05-01T11:00:00.000Z');
            expect(emitted[0].startDate).toBeUndefined();
        });

        it('marks the first existing batch as started when starting a quiz', () => {
            const batch = { id: 1, started: false } as QuizBatch;
            vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({}));
            fixture.componentRef.setInput('quizExercise', buildQuiz({ quizBatches: [batch] }));
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));

            comp.startQuiz();

            expect(emitted[0].status).toBe(QuizStatus.ACTIVE);
            expect(emitted[0].visibleToStudents).toBe(true);
            expect(emitted[0].quizBatches![0].started).toBe(true);
        });

        it('creates a started batch when starting a quiz without batches', () => {
            vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({}));
            fixture.componentRef.setInput('quizExercise', buildQuiz({ quizBatches: undefined }));
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));

            comp.startQuiz();

            expect(emitted[0].quizBatches).toHaveLength(1);
            expect(emitted[0].quizBatches![0].started).toBe(true);
        });

        it('marks the quiz as ended and clears the dialog error on end', () => {
            vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(of({}));
            fixture.componentRef.setInput('quizExercise', buildQuiz({}));
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));
            const errors: string[] = [];
            comp.dialogError$.subscribe((e) => errors.push(e));

            comp.endQuiz();

            expect(emitted[0].quizEnded).toBe(true);
            expect(errors).toEqual(['']);
        });

        it('routes an end failure into the dialog error stream', () => {
            vi.spyOn(quizExerciseApi, 'performActionForQuizExercise').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom' })));
            fixture.componentRef.setInput('quizExercise', buildQuiz({}));
            const errors: string[] = [];
            comp.dialogError$.subscribe((e) => errors.push(e));

            comp.endQuiz();

            expect(errors).toHaveLength(1);
            expect(errors[0]).not.toBe('');
        });

        it('marks only the matching batch as started', () => {
            vi.spyOn(quizExerciseBatchApi, 'startBatch_').mockReturnValue(of({}));
            fixture.componentRef.setInput(
                'quizExercise',
                buildQuiz({
                    quizBatches: [
                        { id: 1, started: false },
                        { id: 2, started: false },
                    ],
                }),
            );
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));

            comp.startBatch(2);

            expect(emitted[0].quizBatches!.map((b) => b.started)).toEqual([false, true]);
        });

        it('does not emit when starting a batch on a quiz without batches', () => {
            vi.spyOn(quizExerciseBatchApi, 'startBatch_').mockReturnValue(of({}));
            fixture.componentRef.setInput('quizExercise', buildQuiz({ quizBatches: undefined }));
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));

            comp.startBatch(2);

            expect(emitted).toHaveLength(0);
        });

        it('appends the new batch on addBatch', () => {
            vi.spyOn(quizExerciseBatchApi, 'addBatch').mockReturnValue(of({ id: 9 }));
            fixture.componentRef.setInput('quizExercise', buildQuiz({ quizBatches: [{ id: 1 }] }));
            const emitted: QuizExercise[] = [];
            comp.handleNewQuizExercise.subscribe((quiz) => emitted.push(quiz));

            comp.addBatch();

            expect(emitted[0].quizBatches!.map((b) => b.id)).toEqual([1, 9]);
        });

        it('alerts and requests a reload when a mutation fails', () => {
            vi.spyOn(quizExerciseBatchApi, 'addBatch').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom' })));
            vi.spyOn(alertService, 'error');
            fixture.componentRef.setInput('quizExercise', buildQuiz({}));
            const reloads: number[] = [];
            comp.loadOne.subscribe((id) => reloads.push(id));

            comp.addBatch();

            expect(alertService.error).toHaveBeenCalledOnce();
            expect(reloads).toEqual([456]);
        });
    });
});
