import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('LearningPathApiService', () => {
    let learningPathApiService: LearningPathApiService;
    let httpClientMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [LearningPathApiService],
        });

        learningPathApiService = TestBed.inject(LearningPathApiService);
        httpClientMock = TestBed.inject(HttpTestingController);
    });

    it('getLearningPathId should return the learning path id', async () => {
        const courseId = 1;
        const learningPathId = 2;
        const result = await learningPathApiService.getLearningPathId(courseId);
        httpClientMock.expectOne(`api/courses/${courseId}/learning-path-id`).flush(learningPathId);
        expect(result).toEqual(learningPathId);
    });
});
