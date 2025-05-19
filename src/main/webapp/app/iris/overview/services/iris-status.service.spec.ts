import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { of } from 'rxjs';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'ngx-webstorage';

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
                { provide: LocalStorageService },
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
        const req = httpMock.expectOne('api/iris/status');
        expect(req.request.method).toBe('GET');
        req.flush({ active: true, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });
    });

    it('should get active status', fakeAsync(() => {
        let isActive: boolean;
        service.getActiveStatus().subscribe((active) => {
            isActive = active;
            expect(isActive).toBeTrue();
        });
        const req = httpMock.expectOne('api/iris/status');
        expect(req.request.method).toBe('GET');
        req.flush({ active: true, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });
        tick();
    }));

    it('should get current rate limit info', fakeAsync(() => {
        const testRateLimitInfo = new IrisRateLimitInformation(100, 50, 0);
        service.handleRateLimitInfo(testRateLimitInfo);

        let rateLimitInfo: IrisRateLimitInformation;
        service.currentRatelimitInfo().subscribe((info) => {
            rateLimitInfo = info;
            expect(rateLimitInfo).toEqual(testRateLimitInfo);
        });
        const req = httpMock.expectOne('api/iris/status');
        expect(req.request.method).toBe('GET');
        req.flush({ active: true, rateLimitInfo: { currentMessageCount: 100, rateLimit: 50, rateLimitTimeframeHours: 0 } });
        tick();
    }));
});
