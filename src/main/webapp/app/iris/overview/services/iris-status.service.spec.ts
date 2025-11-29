import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { of } from 'rxjs';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('IrisStatusService', () => {
    let service: IrisStatusService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisStatusService,
                { provide: WebsocketService, useValue: { connectionState: of({ connected: true, intendedDisconnect: false, wasEverConnectedBefore: true }) } },
                LocalStorageService,
                { provide: ProfileService, useValue: { isProfileActive: jest.fn().mockReturnValue(true) } },
            ],
        });

        service = TestBed.inject(IrisStatusService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
        // No request is made until setCurrentCourse is called
    });

    it('should fetch status when setCurrentCourse is called', fakeAsync(() => {
        service.setCurrentCourse(123);

        const req = httpMock.expectOne('api/iris/courses/123/status');
        expect(req.request.method).toBe('GET');
        req.flush({ active: true, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });
        tick();

        let isActive: boolean | undefined;
        service.getActiveStatus().subscribe((active) => {
            isActive = active;
        });
        expect(isActive).toBeTrue();
    }));

    it('should get active status after course is set', fakeAsync(() => {
        service.setCurrentCourse(456);

        let isActive: boolean | undefined;
        service.getActiveStatus().subscribe((active) => {
            isActive = active;
        });

        const req = httpMock.expectOne('api/iris/courses/456/status');
        expect(req.request.method).toBe('GET');
        req.flush({ active: false, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });
        tick();

        expect(isActive).toBeFalse();
    }));

    it('should get current rate limit info', fakeAsync(() => {
        const testRateLimitInfo = new IrisRateLimitInformation(100, 50, 0);
        service.handleRateLimitInfo(testRateLimitInfo);

        let rateLimitInfo: IrisRateLimitInformation | undefined;
        service.currentRatelimitInfo().subscribe((info) => {
            rateLimitInfo = info;
        });
        expect(rateLimitInfo).toEqual(testRateLimitInfo);
    }));

    it('should not fetch status again if same course is set', fakeAsync(() => {
        service.setCurrentCourse(123);
        const req = httpMock.expectOne('api/iris/courses/123/status');
        req.flush({ active: true, rateLimitInfo: { currentMessageCount: 0, rateLimit: 100, rateLimitTimeframeHours: 24 } });
        tick();

        // Setting same course should not trigger another request
        service.setCurrentCourse(123);
        httpMock.expectNone('api/iris/courses/123/status');
    }));

    it('should fetch status when course changes', fakeAsync(() => {
        service.setCurrentCourse(123);
        const req1 = httpMock.expectOne('api/iris/courses/123/status');
        req1.flush({ active: true, rateLimitInfo: { currentMessageCount: 0, rateLimit: 100, rateLimitTimeframeHours: 24 } });
        tick();

        // Changing to different course should trigger new request
        service.setCurrentCourse(456);
        const req2 = httpMock.expectOne('api/iris/courses/456/status');
        req2.flush({ active: true, rateLimitInfo: { currentMessageCount: 5, rateLimit: 50, rateLimitTimeframeHours: 12 } });
        tick();
    }));
});
