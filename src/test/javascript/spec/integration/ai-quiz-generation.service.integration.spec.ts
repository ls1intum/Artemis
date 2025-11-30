import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import {
    AiQuizGenerationService,
    AiQuizGenerationRequest,
    AiQuizGenerationResponse,
    AiLanguage,
    AiDifficultyLevel,
    AiRequestedSubtype,
} from 'app/quiz/manage/service/ai-quiz-generation.service';

describe('AiQuizGenerationService (HTTP integration)', () => {
    let service: AiQuizGenerationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(AiQuizGenerationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    function samplePayload(): AiQuizGenerationRequest {
        return {
            numberOfQuestions: 1,
            language: AiLanguage.ENGLISH,
            topic: 'Java',
            promptHint: '',
            difficultyLevel: AiDifficultyLevel.MEDIUM,
            requestedSubtype: AiRequestedSubtype.SINGLE_CORRECT,
        };
    }

    it('POSTs to the Hyperion endpoint with the given courseId', () => {
        const courseId = 42;
        const payload = samplePayload();

        let resp: AiQuizGenerationResponse | undefined;
        service.generate(courseId, payload).subscribe((r) => (resp = r));

        const req = httpMock.expectOne(`api/hyperion/quizzes/courses/${courseId}/generate`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);

        const mockRes: AiQuizGenerationResponse = { questions: [], warnings: [] };
        req.flush(mockRes);

        expect(resp).toEqual(mockRes);
    });

    it('maps 404 (Hyperion disabled) to a warnings[] message and empty questions', () => {
        const courseId = 99;
        const payload = samplePayload();

        let resp: AiQuizGenerationResponse | undefined;
        service.generate(courseId, payload).subscribe((r) => (resp = r));

        const req = httpMock.expectOne(`api/hyperion/quizzes/courses/${courseId}/generate`);
        expect(req.request.method).toBe('POST');

        req.flush({ title: 'Not Found' }, { status: 404, statusText: 'Not Found' });

        expect(resp).toBeDefined();
        expect(resp!.questions).toEqual([]);
        expect(resp!.warnings && resp!.warnings.length).toBeGreaterThan(0);
        expect(resp!.warnings![0]).toContain('not available');
    });

    it('returns a generic warning on other HTTP errors', () => {
        const courseId = 7;
        const payload = samplePayload();

        let resp: AiQuizGenerationResponse | undefined;
        service.generate(courseId, payload).subscribe((r) => (resp = r));

        const req = httpMock.expectOne(`api/hyperion/quizzes/courses/${courseId}/generate`);
        req.flush({ message: 'Boom' }, { status: 500, statusText: 'Server Error' });

        expect(resp).toBeDefined();
        expect(resp!.questions).toEqual([]);
        expect(resp!.warnings && resp!.warnings.length).toBeGreaterThan(0);
        expect(resp!.warnings![0]).toContain('HTTP 500');
    });
});
