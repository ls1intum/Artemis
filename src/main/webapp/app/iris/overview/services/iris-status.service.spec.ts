import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { firstValueFrom, of, skip, take } from 'rxjs';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('IrisStatusService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisStatusService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisStatusService,
                { provide: WebsocketService, useValue: { connectionState: of({ connected: true, wasEverConnectedBefore: true }) } },
                LocalStorageService,
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(true) } },
            ],
        });

        service = TestBed.inject(IrisStatusService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
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
