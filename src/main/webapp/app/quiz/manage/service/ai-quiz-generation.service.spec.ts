// src/main/webapp/app/quiz/manage/service/ai-quiz-generation.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AiQuizGenerationService } from './ai-quiz-generation.service';
import { AiDifficultyLevel, AiLanguage, AiRequestedSubtype } from 'app/quiz/manage/service/ai-quiz-generation.enums';

describe('AiQuizGenerationService', () => {
    let service: AiQuizGenerationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [AiQuizGenerationService],
        });
        service = TestBed.inject(AiQuizGenerationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should call correct API endpoint and return questions', () => {
        const payload = {
            numberOfQuestions: 2,
            language: AiLanguage.ENGLISH,
            topic: 'Java Basics',
            difficultyLevel: AiDifficultyLevel.MEDIUM,
            requestedSubtype: AiRequestedSubtype.SINGLE_CORRECT,
        };

        const mockResponse = {
            questions: [
                {
                    title: 'Q1',
                    text: 'What is Java?',
                    subtype: AiRequestedSubtype.SINGLE_CORRECT,
                    tags: [],
                    competencyIds: [],
                    options: [],
                },
            ],
        };

        service.generate(42, payload).subscribe((res) => {
            expect(res.questions).toHaveLength(1);
            expect(res.questions[0].title).toBe('Q1');
        });

        const req = httpMock.expectOne('api/hyperion/quizzes/courses/42/generate');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush(mockResponse);
    });

    it('should propagate HTTP errors', () => {
        const payload = {
            numberOfQuestions: 1,
            language: AiLanguage.ENGLISH,
            topic: 'Error test',
            difficultyLevel: AiDifficultyLevel.EASY,
            requestedSubtype: AiRequestedSubtype.TRUE_FALSE,
        };

        let errorStatus: number | undefined;

        service.generate(5, payload).subscribe({
            next: () => {
                throw new Error('expected an error, but got a response');
            },
            error: (err) => {
                errorStatus = err.status;
            },
        });

        const req = httpMock.expectOne('api/hyperion/quizzes/courses/5/generate');
        expect(req.request.method).toBe('POST');
        req.flush('Internal Server Error', { status: 500, statusText: 'Server Error' });

        expect(errorStatus).toBe(500);
    });
});
