import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuizAiGenerationService } from './quiz-ai-generation.service';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

describe('QuizAiGenerationService', () => {
    setupTestBed({ zoneless: true });
    let service: QuizAiGenerationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(QuizAiGenerationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    function buildMultipleChoiceQuestion(singleChoice: boolean): MultipleChoiceQuestion {
        const question = new MultipleChoiceQuestion();
        question.id = 1;
        question.title = 'HTTP Methods';
        question.text = 'What is a REST method?';
        question.hint = 'Think about REST';
        question.singleChoice = singleChoice;

        const correct = new AnswerOption();
        correct.text = 'Option A';
        correct.isCorrect = true;

        const wrong = new AnswerOption();
        wrong.text = 'Option B';
        wrong.isCorrect = false;

        question.answerOptions = [correct, wrong];
        return question;
    }

    function expectRefineRequest(courseId: number): ReturnType<HttpTestingController['expectOne']> {
        return httpMock.expectOne((r) => r.method === 'POST' && r.url.includes(`courses/${courseId}/quiz-exercises/refine-question`));
    }

    function flushRefineResponse(req: ReturnType<HttpTestingController['expectOne']>, type: 'single-choice' | 'multiple-choice', optionCount: number): void {
        const options = Array.from({ length: optionCount }, (_, i) => ({ text: `Option ${i}`, correct: i === 0 }));
        req.flush({ question: { type, title: 'A Question', questionText: 'Some question text?', options }, reasoning: 'Some explanation.' });
    }

    describe('refineMultipleChoiceQuestion', () => {
        it('should set type to single-choice when question.singleChoice is true', () => {
            const question = buildMultipleChoiceQuestion(true);
            service.refineMultipleChoiceQuestion(1, question, 'some prompt').subscribe();

            const req = expectRefineRequest(1);
            expect(req.request.body.question.type).toBe('single-choice');
            flushRefineResponse(req, 'single-choice', 2);
        });

        it('should set type to multiple-choice when question.singleChoice is false', () => {
            const question = buildMultipleChoiceQuestion(false);
            service.refineMultipleChoiceQuestion(1, question, 'some prompt').subscribe();

            const req = expectRefineRequest(1);
            expect(req.request.body.question.type).toBe('multiple-choice');
            flushRefineResponse(req, 'multiple-choice', 3);
        });

        it('should include question fields and refinement prompt in request body', () => {
            const question = buildMultipleChoiceQuestion(true);
            service.refineMultipleChoiceQuestion(42, question, 'Make it harder').subscribe();

            const req = expectRefineRequest(42);
            expect(req.request.body.refinementPrompt).toBe('Make it harder');
            expect(req.request.body.question.title).toBe('HTTP Methods');
            expect(req.request.body.question.questionText).toBe('What is a REST method?');
            expect(req.request.body.question.hint).toBe('Think about REST');
            expect(req.request.body.question.options).toHaveLength(2);
            expect(req.request.body.question.options[0].correct).toBe(true);
            expect(req.request.body.question.options[1].correct).toBe(false);
            flushRefineResponse(req, 'single-choice', 2);
        });

        it('should map response fields back to MultipleChoiceQuestion', () => {
            const question = buildMultipleChoiceQuestion(false);
            let result: { refinedQuestion: MultipleChoiceQuestion; reasoning: string } | undefined;

            service.refineMultipleChoiceQuestion(1, question, 'improve').subscribe((r) => (result = r));

            const req = expectRefineRequest(1);
            req.flush({
                question: {
                    type: 'multiple-choice',
                    title: 'Refined Title',
                    questionText: 'Refined text?',
                    hint: 'A hint',
                    options: [
                        { text: 'Option A', correct: true, hint: 'hint a', explanation: 'exp a' },
                        { text: 'Option B', correct: false },
                    ],
                },
                reasoning: 'Some explanation.',
            });

            expect(result).toBeDefined();
            expect(result!.refinedQuestion.title).toBe('Refined Title');
            expect(result!.refinedQuestion.text).toBe('Refined text?');
            expect(result!.refinedQuestion.hint).toBe('A hint');
            expect(result!.refinedQuestion.singleChoice).toBe(false);
            expect(result!.refinedQuestion.answerOptions).toHaveLength(2);
            expect(result!.refinedQuestion.answerOptions![0].isCorrect).toBe(true);
            expect(result!.refinedQuestion.answerOptions![0].hint).toBe('hint a');
            expect(result!.refinedQuestion.answerOptions![0].explanation).toBe('exp a');
            expect(result!.refinedQuestion.answerOptions![1].isCorrect).toBe(false);
            expect(result!.refinedQuestion.hasCorrectOption).toBe(true);
            expect(result!.reasoning).toBeDefined();
        });

        it('should fall back to "Untitled Question" when question title is missing', () => {
            const question = buildMultipleChoiceQuestion(true);
            question.title = undefined;
            service.refineMultipleChoiceQuestion(1, question, 'improve').subscribe();

            const req = expectRefineRequest(1);
            expect(req.request.body.question.title).toBe('Untitled Question');
            flushRefineResponse(req, 'single-choice', 2);
        });
    });

    describe('refineAllMultipleChoiceQuestions', () => {
        function expectBulkRefineRequest(courseId: number): ReturnType<HttpTestingController['expectOne']> {
            return httpMock.expectOne((r) => r.method === 'POST' && r.url.includes(`courses/${courseId}/quiz-exercises/refine-all-questions`));
        }

        function flushBulkRefineResponse(req: ReturnType<HttpTestingController['expectOne']>, count: number): void {
            const refinements = Array.from({ length: count }, (_, i) => ({
                question: { type: 'single-choice', title: `Refined Q${i}`, questionText: `Refined text ${i}?`, options: [{ text: 'A', correct: true }] },
                reasoning: `Reasoning ${i}`,
            }));
            req.flush({ refinements });
        }

        it('should send all questions and the shared refinement prompt in a single request', () => {
            const q1 = buildMultipleChoiceQuestion(true);
            const q2 = buildMultipleChoiceQuestion(false);
            service.refineAllMultipleChoiceQuestions(1, [q1, q2], 'Make harder').subscribe();

            const req = expectBulkRefineRequest(1);
            expect(req.request.body.refinementPrompt).toBe('Make harder');
            expect(req.request.body.questions).toHaveLength(2);
            expect(req.request.body.questions[0].type).toBe('single-choice');
            expect(req.request.body.questions[1].type).toBe('multiple-choice');
            flushBulkRefineResponse(req, 2);
        });

        it('should map question fields correctly for each question in the bulk request', () => {
            const q = buildMultipleChoiceQuestion(false);
            service.refineAllMultipleChoiceQuestions(42, [q], 'improve').subscribe();

            const req = expectBulkRefineRequest(42);
            const sent = req.request.body.questions[0];
            expect(sent.title).toBe('HTTP Methods');
            expect(sent.questionText).toBe('What is a REST method?');
            expect(sent.hint).toBe('Think about REST');
            expect(sent.options).toHaveLength(2);
            expect(sent.options[0].correct).toBe(true);
            expect(sent.options[1].correct).toBe(false);
            flushBulkRefineResponse(req, 1);
        });

        it('should fall back to "Untitled Question" for questions with missing titles', () => {
            const q = buildMultipleChoiceQuestion(true);
            q.title = undefined;
            service.refineAllMultipleChoiceQuestions(1, [q], 'improve').subscribe();

            const req = expectBulkRefineRequest(1);
            expect(req.request.body.questions[0].title).toBe('Untitled Question');
            flushBulkRefineResponse(req, 1);
        });

        it('should return refined questions and reasonings in the same order as the input', () => {
            const q1 = buildMultipleChoiceQuestion(true);
            q1.title = 'Q1';
            const q2 = buildMultipleChoiceQuestion(false);
            q2.title = 'Q2';
            let results: { refinedQuestion: MultipleChoiceQuestion; reasoning: string }[] | undefined;

            service.refineAllMultipleChoiceQuestions(1, [q1, q2], 'improve').subscribe((r) => (results = r));

            const req = expectBulkRefineRequest(1);
            req.flush({
                refinements: [
                    {
                        question: { type: 'single-choice', title: 'Refined Q1', questionText: 'Refined text 1?', options: [{ text: 'A', correct: true }] },
                        reasoning: 'Reasoning 1',
                    },
                    {
                        question: {
                            type: 'multiple-choice',
                            title: 'Refined Q2',
                            questionText: 'Refined text 2?',
                            options: [
                                { text: 'B', correct: false },
                                { text: 'C', correct: true },
                            ],
                        },
                        reasoning: 'Reasoning 2',
                    },
                ],
            });

            expect(results).toHaveLength(2);
            expect(results![0].refinedQuestion.title).toBe('Refined Q1');
            expect(results![0].reasoning).toBe('Reasoning 1');
            expect(results![1].refinedQuestion.title).toBe('Refined Q2');
            expect(results![1].reasoning).toBe('Reasoning 2');
        });

        it('should apply refined answer options back to the original question objects', () => {
            const q = buildMultipleChoiceQuestion(false);
            let result: { refinedQuestion: MultipleChoiceQuestion; reasoning: string }[] | undefined;

            service.refineAllMultipleChoiceQuestions(1, [q], 'improve').subscribe((r) => (result = r));

            const req = expectBulkRefineRequest(1);
            req.flush({
                refinements: [
                    {
                        question: {
                            type: 'multiple-choice',
                            title: 'New Title',
                            questionText: 'New text?',
                            hint: 'New hint',
                            options: [
                                { text: 'Opt A', correct: true, hint: 'h', explanation: 'e' },
                                { text: 'Opt B', correct: false },
                            ],
                        },
                        reasoning: 'Some reasoning.',
                    },
                ],
            });

            expect(result![0].refinedQuestion.title).toBe('New Title');
            expect(result![0].refinedQuestion.text).toBe('New text?');
            expect(result![0].refinedQuestion.hint).toBe('New hint');
            expect(result![0].refinedQuestion.answerOptions).toHaveLength(2);
            expect(result![0].refinedQuestion.answerOptions![0].isCorrect).toBe(true);
            expect(result![0].refinedQuestion.answerOptions![0].hint).toBe('h');
            expect(result![0].refinedQuestion.answerOptions![1].isCorrect).toBe(false);
            expect(result![0].refinedQuestion.hasCorrectOption).toBe(true);
        });
    });
});
