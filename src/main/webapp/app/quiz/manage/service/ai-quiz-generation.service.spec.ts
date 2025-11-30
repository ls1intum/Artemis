import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AiDifficultyLevel, AiLanguage, AiQuizGenerationService, AiRequestedSubtype } from './ai-quiz-generation.service';

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
                    options: [],
                },
            ],
            warnings: [],
        };

        service.generate(42, payload).subscribe((res) => {
            expect(res.questions).toHaveLength(1);
            expect(res.questions[0].title).toBe('Q1');
            expect(res.warnings?.length).toBe(0);
        });

        const req = httpMock.expectOne('api/hyperion/quizzes/courses/42/generate');
        expect(req.request.method).toBe('POST');
        req.flush(mockResponse);
    });

    it('should catch errors and return a warning message', () => {
        const payload = {
            numberOfQuestions: 1,
            language: AiLanguage.ENGLISH,
            topic: 'Error test',
            difficultyLevel: AiDifficultyLevel.EASY,
            requestedSubtype: AiRequestedSubtype.TRUE_FALSE,
        };

        service.generate(5, payload).subscribe((res) => {
            expect(res.questions).toEqual([]);
            expect(res.warnings?.[0]).toContain('HTTP 500');
        });

        const req = httpMock.expectOne('api/hyperion/quizzes/courses/5/generate');
        req.flush('Internal Server Error', { status: 500, statusText: 'Server Error' });
    });

    it('should describe network and 404 errors correctly', () => {
        const payload = {
            numberOfQuestions: 1,
            language: AiLanguage.GERMAN,
            topic: 'Network test',
            difficultyLevel: AiDifficultyLevel.HARD,
            requestedSubtype: AiRequestedSubtype.MULTI_CORRECT,
        };

        service.generate(1, payload).subscribe((res) => {
            expect(res.warnings?.[0]).toContain('Network error');
        });

        const req1 = httpMock.expectOne('api/hyperion/quizzes/courses/1/generate');
        req1.error(new ProgressEvent('network'), { status: 0 });

        service.generate(2, payload).subscribe((res) => {
            expect(res.warnings?.[0]).toContain('not available');
        });

        const req2 = httpMock.expectOne('api/hyperion/quizzes/courses/2/generate');
        req2.flush('Not Found', { status: 404, statusText: 'Not Found' });
    });
});
