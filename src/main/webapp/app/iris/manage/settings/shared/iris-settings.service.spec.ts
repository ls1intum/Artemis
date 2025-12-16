import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { provideHttpClient } from '@angular/common/http';
import { IrisCourseSettingsDTO, IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

describe('Iris Settings Service', () => {
    let service: IrisSettingsService;
    let httpMock: HttpTestingController;

    const mockCourseSettings: IrisCourseSettingsWithRateLimitDTO = {
        courseId: 1,
        settings: {
            enabled: true,
            customInstructions: 'Test instructions',
            variant: 'default',
            rateLimit: { requests: 100, timeframeHours: 24 },
        },
        effectiveRateLimit: { requests: 100, timeframeHours: 24 },
        applicationRateLimitDefaults: { requests: 50, timeframeHours: 12 },
    };

    const mockUpdateSettings: IrisCourseSettingsDTO = {
        enabled: false,
        customInstructions: 'Updated instructions',
        variant: 'advanced',
        rateLimit: { requests: 200, timeframeHours: 48 },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisSettingsService],
        });
        service = TestBed.inject(IrisSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        service.clearCache(); // Clear cache between tests
    });

    describe('getCourseSettings', () => {
        it('should get course settings', fakeAsync(() => {
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(mockCourseSettings));

            const req = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/1/iris-settings' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));

        it('should reuse pending request when getting course settings', fakeAsync(() => {
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(mockCourseSettings));
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(mockCourseSettings));

            // Should only trigger one HTTP request
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));

        it('should reuse cached result after getting course settings', fakeAsync(() => {
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Second request should use cache, no HTTP request
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(mockCourseSettings));
            httpMock.expectNone({ method: 'GET' });
            tick();
        }));

        it('should trigger new request after cache duration', fakeAsync(() => {
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Advance time by more than CACHE_DURATION
            tick((IrisSettingsService as any).CACHE_DURATION + 1);

            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newReq = httpMock.expectOne({ method: 'GET' });
            newReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));

        it('should clear pending request and allow retry after failure', fakeAsync(() => {
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe({
                    error: (err) => expect(err.status).toBe(500),
                });
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush({}, { status: 500, statusText: 'Internal Server Error' });
            tick();

            // Retry should trigger a new request
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(mockCourseSettings));
            const retryReq = httpMock.expectOne({ method: 'GET' });
            retryReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));

        it('should handle undefined response body', fakeAsync(() => {
            service
                .getCourseSettingsWithRateLimit(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toBeUndefined());

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(null, { status: 200, statusText: 'Ok' });
            tick();
        }));
    });

    describe('updateCourseSettings', () => {
        it('should update course settings', fakeAsync(() => {
            service
                .updateCourseSettings(1, mockUpdateSettings)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockCourseSettings));

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/iris/courses/1/iris-settings' });
            expect(req.request.body).toEqual(mockUpdateSettings);
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));

        it('should invalidate cache on successful update', fakeAsync(() => {
            // First, populate the cache
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const getReq = httpMock.expectOne({ method: 'GET' });
            getReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Verify cache is populated (no new request)
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            httpMock.expectNone({ method: 'GET' });

            // Update settings
            service.updateCourseSettings(1, mockUpdateSettings).pipe(take(1)).subscribe();
            const putReq = httpMock.expectOne({ method: 'PUT' });
            putReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Next getCourseSettings should trigger a new request (cache invalidated)
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newGetReq = httpMock.expectOne({ method: 'GET' });
            newGetReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));
    });

    describe('invalidateCacheForCourse', () => {
        it('should invalidate cache for specific course', fakeAsync(() => {
            // Populate cache for course 1
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req1 = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/1/iris-settings' });
            req1.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Populate cache for course 2
            service.getCourseSettingsWithRateLimit(2).pipe(take(1)).subscribe();
            const req2 = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/2/iris-settings' });
            req2.flush({ ...mockCourseSettings, courseId: 2 }, { status: 200, statusText: 'Ok' });
            tick();

            // Invalidate cache for course 1
            service.invalidateCacheForCourse(1);

            // Course 1 should trigger new request
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newReq1 = httpMock.expectOne({ method: 'GET', url: 'api/iris/courses/1/iris-settings' });
            newReq1.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Course 2 should use cache (no new request)
            service.getCourseSettingsWithRateLimit(2).pipe(take(1)).subscribe();
            httpMock.expectNone({ method: 'GET' });
        }));
    });

    describe('clearCache', () => {
        it('should clear all cached settings', fakeAsync(() => {
            // Populate cache
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();

            // Clear cache
            service.clearCache();

            // Should trigger new request
            service.getCourseSettingsWithRateLimit(1).pipe(take(1)).subscribe();
            const newReq = httpMock.expectOne({ method: 'GET' });
            newReq.flush(mockCourseSettings, { status: 200, statusText: 'Ok' });
            tick();
        }));
    });
});
