import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoursePracticeQuizComponent } from './course-practice-quiz.component';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from '../../shared/entities/quiz-question.model';
import { MockBuilder } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { CoursePracticeQuizService } from 'app/quiz/overview/service/course-practice-quiz.service';
import { MockSyncStorage } from 'src/test/javascript/spec/helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { Result } from '../../../exercise/shared/entities/result/result.model';
import { QuizParticipationService } from '../service/quiz-participation.service';
import { AlertService } from '../../../shared/service/alert.service';

const question1: QuizQuestion = {
    id: 1,
    type: QuizQuestionType.DRAG_AND_DROP,
    points: 1,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question2: QuizQuestion = {
    id: 2,
    type: QuizQuestionType.MULTIPLE_CHOICE,
    points: 2,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question3: QuizQuestion = {
    id: 3,
    type: QuizQuestionType.SHORT_ANSWER,
    points: 3,
    randomizeOrder: false,
    invalid: false,
    exportQuiz: false,
};

describe('CoursePracticeQuizComponent', () => {
    let component: CoursePracticeQuizComponent;
    let fixture: ComponentFixture<CoursePracticeQuizComponent>;
    let quizService: CoursePracticeQuizService;

    const mockQuestions = [question1, question2, question3];

    beforeEach(async () => {
        await MockBuilder(CoursePracticeQuizComponent)
            .keep(Router)
            .provide([
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: 1 }),
                        },
                    },
                },
            ]);
        quizService = TestBed.inject(CoursePracticeQuizService);
        jest.spyOn(quizService, 'getQuizQuestions').mockReturnValue(of([question1, question2, question3]));

        fixture = TestBed.createComponent(CoursePracticeQuizComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should extract courseId from route params', () => {
        expect(component.courseId()).toBe(1);
    });

    it('should load questions from service', () => {
        expect(component.questionsSignal()).toEqual(mockQuestions);
        expect(component.questions()).toEqual(mockQuestions);
    });

    it('should check for last question', () => {
        component.currentIndex.set(0);
        expect(component.isLastQuestion()).toBeFalsy();
        component.currentIndex.set(2);
        expect(component.isLastQuestion()).toBeTruthy();
    });

    it('should check if questions is empty', () => {
        jest.spyOn(component, 'questions').mockReturnValue([]);
        component.currentIndex.set(1);
        expect(component.isLastQuestion()).toBeTruthy();
        expect(component.currentQuestion()).toBeUndefined();
    });

    it('should return the current question based on currentIndex', () => {
        component.currentIndex.set(0);
        expect(component.currentQuestion()).toBe(question1);
    });

    it('should go to the next question and call initQuestion', () => {
        component.currentIndex.set(0);
        const initQuestionSpy = jest.spyOn(component, 'initQuestion');
        component.nextQuestion();
        expect(component.currentIndex()).toBe(1);
        expect(initQuestionSpy).toHaveBeenCalledOnce();
        expect(initQuestionSpy).toHaveBeenCalledWith(question2);
    });

    it('should navigate to practice when on last question', () => {
        component.currentIndex.set(2);
        const navigateSpy = jest.spyOn(component, 'navigateToPractice');
        component.nextQuestion();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should init the current question', () => {
        component.initQuestion(question1);
        expect(component.showingResult).toBeFalsy();
        expect(component.dragAndDropMappings).toEqual([]);
        component.initQuestion(question2);
        expect(component.showingResult).toBeFalsy();
        expect(component.selectedAnswerOptions).toEqual([]);
        component.initQuestion(question3);
        expect(component.showingResult).toBeFalsy();
        expect(component.shortAnswerSubmittedTexts).toEqual([]);
    });

    describe('onSubmit', () => {
        it('should submit drag and drop question and apply selection', () => {
            component.currentIndex.set(0);
            component.dragAndDropMappings = [{ id: 100 } as any];

            const mockResult: Result = {
                id: 1,
                score: 100,
                submission: {
                    submittedAnswers: [
                        {
                            quizQuestion: question1,
                            scoreInPoints: 1,
                            mappings: [{ id: 100 }],
                        },
                    ],
                } as any,
            } as Result;

            const submitSpy = jest.spyOn(TestBed.inject(QuizParticipationService), 'submitForPractice').mockReturnValue(of(new HttpResponse({ body: mockResult })));

            component.onSubmit();

            expect(submitSpy).toHaveBeenCalledOnce();
        });

        it('should submit multiple choice question and apply selection', () => {
            component.currentIndex.set(1);
            component.selectedAnswerOptions = [{ id: 200 } as any];

            const mockResult: Result = {
                id: 2,
                score: 100,
                submission: {
                    submittedAnswers: [
                        {
                            quizQuestion: question2,
                            scoreInPoints: 2,
                            selectedOptions: [{ id: 200 }],
                        },
                    ],
                } as any,
            } as Result;

            const submitSpy = jest.spyOn(TestBed.inject(QuizParticipationService), 'submitForPractice').mockReturnValue(of(new HttpResponse({ body: mockResult })));

            component.onSubmit();

            expect(submitSpy).toHaveBeenCalledOnce();
        });

        it('should submit short answer question and apply selection', () => {
            component.currentIndex.set(2);
            component.shortAnswerSubmittedTexts = [{ id: 300 } as any];

            const mockResult: Result = {
                id: 3,
                score: 100,
                submission: {
                    submittedAnswers: [
                        {
                            quizQuestion: question3,
                            scoreInPoints: 3,
                            submittedTexts: [{ id: 300 }],
                        },
                    ],
                } as any,
            } as Result;

            const submitSpy = jest.spyOn(TestBed.inject(QuizParticipationService), 'submitForPractice').mockReturnValue(of(new HttpResponse({ body: mockResult })));

            component.onSubmit();

            expect(submitSpy).toHaveBeenCalledOnce();
        });

        it('should handle submit error and call onSubmitError', () => {
            const error: any = {
                message: 'Fehler beim Absenden',
                headers: {
                    get: (key: string) => (key === 'X-artemisApp-message' ? 'Fehler beim Absenden' : null),
                },
            };

            const submitSpy = jest.spyOn(TestBed.inject(QuizParticipationService), 'submitForPractice').mockImplementation(
                () =>
                    ({
                        subscribe: ({ error: errorCallback }: any) => errorCallback(error),
                    }) as any,
            );

            const onSubmitErrorSpy = jest.spyOn(component, 'onSubmitError');

            component.onSubmit();

            expect(submitSpy).toHaveBeenCalledOnce();
            expect(onSubmitErrorSpy).toHaveBeenCalledWith(error);
        });
    });

    it('should handle submit error and show alert', () => {
        const alertService = TestBed.inject(AlertService);
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        component.isSubmitting = true;

        const error: any = {
            message: 'Some error',
            headers: {
                get: (key: string) => (key === 'X-artemisApp-message' ? 'Fehler beim Absenden' : null),
            },
        };

        component.onSubmitError(error);

        expect(component.isSubmitting).toBeFalsy();
        expect(addAlertSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                type: expect.anything(),
                message: expect.stringContaining('Fehler beim Absenden'),
                disableTranslation: true,
            }),
        );
    });

    it('should navigate to practice', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.navigateToPractice();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'practice']);
    });
});
