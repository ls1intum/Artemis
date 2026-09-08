import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { firstValueFrom, of, skip, take } from 'rxjs';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('IrisStatusService', () => {
    let service: IrisStatusService;
    let httpMock: HttpTestingController;

    /** Builds the service with the iris module feature either active or inactive, since that decides whether it does anything at all. */
    function configureWithIrisModule(active: boolean) {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisStatusService,
                { provide: WebsocketService, useValue: { connectionState: of({ connected: true, wasEverConnectedBefore: true }) } },
                LocalStorageService,
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(active) } },
            ],
        });

        service = TestBed.inject(IrisStatusService);
        httpMock = TestBed.inject(HttpTestingController);
    }

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('when the iris module is active', () => {
        beforeEach(() => {
            configureWithIrisModule(true);
        });

        it('should be created', () => {
            expect(service).toBeTruthy();
            // No request is made until setCurrentCourse is called
        });

        it('should fetch status when setCurrentCourse is called', async () => {
            const activePromise = firstValueFrom(service.getActiveStatus().pipe(skip(1), take(1)));
            service.setCurrentCourse(123);

            const req = httpMock.expectOne('api/iris/courses/123/status');
            expect(req.request.method).toBe('GET');
            req.flush({ active: true, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });

            await expect(activePromise).resolves.toBe(true);
        });

        it('should get active status after course is set', async () => {
            const activePromise = firstValueFrom(service.getActiveStatus().pipe(skip(1), take(1)));
            service.setCurrentCourse(456);

            const req = httpMock.expectOne('api/iris/courses/456/status');
            expect(req.request.method).toBe('GET');
            req.flush({ active: false, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });

            await expect(activePromise).resolves.toBe(false);
        });

        it('should get current rate limit info', async () => {
            const testRateLimitInfo = new IrisRateLimitInformation(100, 50, 0);
            service.handleRateLimitInfo(testRateLimitInfo);

            const rateLimitInfo = await firstValueFrom(service.currentRatelimitInfo().pipe(take(1)));
            expect(rateLimitInfo).toEqual(testRateLimitInfo);
        });

        it('should not fetch status again if same course is set', async () => {
            service.setCurrentCourse(123);
            const req = httpMock.expectOne('api/iris/courses/123/status');
            req.flush({ active: true, rateLimitInfo: { currentMessageCount: 0, rateLimit: 100, rateLimitTimeframeHours: 24 } });

            // Setting same course should not trigger another request
            service.setCurrentCourse(123);
            httpMock.expectNone('api/iris/courses/123/status');
        });

        it('should fetch status when course changes', async () => {
            service.setCurrentCourse(123);
            const req1 = httpMock.expectOne('api/iris/courses/123/status');
            req1.flush({ active: true, rateLimitInfo: { currentMessageCount: 0, rateLimit: 100, rateLimitTimeframeHours: 24 } });

            // Changing to different course should trigger new request
            service.setCurrentCourse(456);
            const req2 = httpMock.expectOne('api/iris/courses/456/status');
            req2.flush({ active: true, rateLimitInfo: { currentMessageCount: 5, rateLimit: 50, rateLimitTimeframeHours: 12 } });
        });
    });

    describe('when the iris module is not active', () => {
        beforeEach(() => {
            configureWithIrisModule(false);
        });

        // The iris REST controllers are not registered without the module, so any status request answers 404 in the
        // browser console. Components outside the iris route guard inject IrisChatService unconditionally, and its
        // constructor sets the current course, so the guard has to live in the service rather than at the call sites.
        it('should not request the status when a course is set', () => {
            service.setCurrentCourse(123);

            httpMock.expectNone('api/iris/courses/123/status');
        });

        it('should report iris as inactive without contacting the server', async () => {
            service.setCurrentCourse(123);

            await expect(firstValueFrom(service.getActiveStatus().pipe(take(1)))).resolves.toBe(false);
        });
    });
});
