import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { of } from 'rxjs';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { IrisRateLimitInformation } from 'app/entities/iris/iris-ratelimit-info.model';
import { provideHttpClient } from '@angular/common/http';

describe('IrisStatusService', () => {
    let service: IrisStatusService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                IrisStatusService,
                { provide: JhiWebsocketService, useValue: { connectionState: of({ connected: true, intendedDisconnect: false, wasEverConnectedBefore: true }) } },
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
