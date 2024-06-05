import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { TestBed, waitForAsync } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('LearningPathApiService', () => {
    let httpClient: HttpTestingController;
    let learningPathApiService: LearningPathApiService;

    const url = 'api';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [LearningPathApiService],
        });

        learningPathApiService = TestBed.inject(LearningPathApiService);
        httpClient = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should create', () => {
        expect(learningPathApiService).toBeTruthy();
    });

    it('should get learning path id', waitForAsync(async () => {
        const courseId = 1;
        const learningPathId = 1;

        const methodCall = learningPathApiService.getLearningPathId(courseId);
        const req = httpClient.expectOne({ method: 'GET', url: `${url}/courses/${courseId}/learning-path-id` });
        req.flush(learningPathId);

        const result = await methodCall;
        expect(result).toEqual(learningPathId);
    }));
});
