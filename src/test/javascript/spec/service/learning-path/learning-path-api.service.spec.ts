import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { TestBed, waitForAsync } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LearningObjectType } from 'app/entities/competency/learning-path.model';

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

    it('should initialize', () => {
        expect(learningPathApiService).toBeTruthy();
    });

    it('should get learning path id', waitForAsync(async () => {
        const courseId = 1;

        const methodCall = learningPathApiService.getLearningPathId(courseId);
        httpClient.expectOne({ method: 'GET', url: `${url}/courses/${courseId}/learning-path-id` });
        await methodCall;
    }));

    it('should get learning path navigation', waitForAsync(async () => {
        const learningPathId = 1;
        const learningObjectId = 2;

        const learningObjectType = LearningObjectType.LECTURE;
        const methodCall = learningPathApiService.getLearningPathNavigation(learningPathId, learningObjectId, learningObjectType);
        httpClient.expectOne({
            method: 'GET',
            url: `${url}/learning-path/${learningPathId}/navigation?learningObjectId=${learningObjectId}&learningObjectType=${learningObjectType}`,
        });
        await methodCall;
    }));

    it('should get learning path navigation overview', waitForAsync(async () => {
        const learningPathId = 1;

        const methodCall = learningPathApiService.getLearningPathNavigationOverview(learningPathId);
        httpClient.expectOne({ method: 'GET', url: `${url}/learning-path/${learningPathId}/navigation-overview` });
        await methodCall;
    }));

    it('should generate learning path', waitForAsync(async () => {
        const courseId = 1;

        const methodCall = learningPathApiService.generateLearningPath(courseId);
        httpClient.expectOne({ method: 'POST', url: `${url}/courses/${courseId}/learning-path` });
        await methodCall;
    }));
});
