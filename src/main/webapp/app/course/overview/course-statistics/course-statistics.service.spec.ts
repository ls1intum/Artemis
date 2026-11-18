import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseStatisticsService } from './course-statistics.service';

describe('CourseStatisticsService', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
    afterEach(() => TestBed.inject(HttpTestingController).verify());

    it('requests current aggregate material counts for the selected course', () => {
        const response = [{ lectureId: 1, type: 'text' as const, total: 8, completed: 3 }];
        const next = vi.fn();
        TestBed.inject(CourseStatisticsService).getLearningStatistics(589).subscribe(next);
        const request = TestBed.inject(HttpTestingController).expectOne('api/lecture/courses/589/learning-statistics');
        expect(request.request.method).toBe('GET');
        request.flush(response);
        expect(next).toHaveBeenCalledWith(response);
    });

    it('retains legitimate zero scores and missing comparison values on every fresh request', () => {
        const service = TestBed.inject(CourseStatisticsService);
        for (let i = 0; i < 2; i++) {
            const next = vi.fn();
            service.getPeerComparison(589).subscribe(next);
            TestBed.inject(HttpTestingController)
                .expectOne('api/exercise/courses/589/charts/peer-comparison')
                .flush({ exercises: [{ exerciseId: 1, ownScore: 0 }], groupCount: 0 });
            expect(next).toHaveBeenCalledWith({ exercises: [{ exerciseId: 1, ownScore: 0 }], groupCount: 0 });
        }
    });

    it('propagates an unavailable source so the page can show an independent retry state', () => {
        const error = vi.fn();
        TestBed.inject(CourseStatisticsService).getLearningStatistics(589).subscribe({ error });
        TestBed.inject(HttpTestingController).expectOne('api/lecture/courses/589/learning-statistics').flush({}, { status: 503, statusText: 'Unavailable' });
        expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 503 }));
    });
});
